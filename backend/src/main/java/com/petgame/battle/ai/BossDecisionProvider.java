package com.petgame.battle.ai;

import com.petgame.battle.calculator.DamageCalculator;
import com.petgame.battle.calculator.HealCalculator;
import com.petgame.battle.calculator.StatusModifiers;
import com.petgame.battle.engine.BattleContext;
import com.petgame.battle.model.BattleAction;
import com.petgame.battle.model.BattleSide;
import com.petgame.battle.model.BattleUnit;
import com.petgame.config.GameConfigRegistry;
import com.petgame.config.model.SkillConfig;
import com.petgame.config.model.StatusEffectConfig;
import com.petgame.config.model.SystemRuleConfig;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Boss AI 决策器（阶段 7，评分式规则 AI）。
 * <p>
 * 决策模型：候选行动生成（技能 × 目标）→ 过滤非法 → 评分 → 选择最高分
 * （评分接近的候选间用战斗统一随机小幅随机，固定种子可复现）。
 * <ul>
 *   <li>属性克制：读取现有克制表，估算伤害中直接体现克制倍率</li>
 *   <li>低血目标：HP% 越低攻击评分越高；预计伤害可击杀时追加斩杀奖励</li>
 *   <li>技能冷却：只从 {@link BattleUnit#getReadySkillIds()} 中选择，沉默时防御</li>
 *   <li>阶段策略：阶段索引 = 已激活 phaseTrigger 数量（引擎维护），
 *       一阶段均衡 / 二阶段进攻 / 三阶段爆发（倍率配置化）</li>
 *   <li>控制策略：复用目标 controlResistance + 连续控制衰减估算成功率，
 *       目标已受控时大幅降权，避免机械连续控制</li>
 *   <li>治疗策略：友方 HP% &lt; 阈值时治疗优先级明显提高，过量治疗不计分，接近满血不治疗</li>
 *   <li>不读取玩家等级/战力做动态缩放（需求 §80），难度仅来源于配置与战场状态</li>
 * </ul>
 * AI 只做估算排序；伤害/克制/控制成功率的实际结算仍由 BattleEngine 负责。
 * 详细设计见 docs/BOSS_AI_REWORK.md。
 */
@Component
public class BossDecisionProvider implements DecisionProvider {

    private final GameConfigRegistry registry;
    private final HealCalculator healCalculator;

    public BossDecisionProvider(GameConfigRegistry registry) {
        this.registry = registry;
        this.healCalculator = new HealCalculator(registry);
    }

    @Override
    public List<BattleAction> decide(BattleContext ctx, BattleSide side) {
        List<BattleAction> actions = new ArrayList<>();
        BattleSide enemyOfSide = ctx.getOpposite(side);
        for (BattleUnit unit : side.getActiveAliveUnits()) {
            actions.add(decideForUnit(ctx, unit, side, enemyOfSide));
        }
        return actions;
    }

    /** 单个单位的决策：生成候选行动并选出最高分（接近分随机）。 */
    private BattleAction decideForUnit(BattleContext ctx, BattleUnit unit,
                                       BattleSide ownSide, BattleSide enemyOfSide) {
        List<String> readySkills = unit.getReadySkillIds();
        // 沉默时无技能可用，交给引擎兜底为防御（与 WildEnemyDecisionProvider 一致）
        boolean silenced = StatusModifiers.of(unit, registry.getStatusIndex()).isSilenced();
        if (silenced || readySkills.isEmpty()) {
            return BattleAction.defend(unit.getUnitId());
        }

        SystemRuleConfig.BossAiConfig ai = registry.getSystemRules().getBossAi();
        int phase = currentPhase(unit);
        List<Candidate> candidates = new ArrayList<>();

        for (String skillId : readySkills) {
            SkillConfig skill = registry.getSkill(skillId);
            if (skill == null) {
                continue;
            }
            String effectType = skill.getEffectType() != null ? skill.getEffectType().toUpperCase() : "";
            switch (effectType) {
                case "DAMAGE" -> generateAttackCandidates(candidates, ctx, unit, skill, enemyOfSide, phase, ai);
                case "HEAL" -> generateHealCandidates(candidates, unit, skill, ownSide, phase, ai);
                case "SHIELD" -> generateShieldCandidates(candidates, unit, skill, ownSide, phase, ai);
                default -> generateStatusSkillCandidates(candidates, unit, skill, enemyOfSide, ownSide, phase, ai);
            }
        }

        if (candidates.isEmpty()) {
            return BattleAction.defend(unit.getUnitId());
        }
        Candidate best = pickBest(ctx, candidates, ai.getTieTolerance());
        return BattleAction.skill(unit.getUnitId(), best.skillId, best.targetId);
    }

    // ---- 候选生成 ----

    /** 攻击候选：单体技能对每个存活敌方目标各一个候选；群体技能一个候选（求和）。 */
    private void generateAttackCandidates(List<Candidate> out, BattleContext ctx, BattleUnit caster,
                                          SkillConfig skill, BattleSide enemyOfSide, int phase,
                                          SystemRuleConfig.BossAiConfig ai) {
        List<BattleUnit> targets = enemyOfSide.getActiveAliveUnits();
        if (targets.isEmpty()) {
            return;
        }
        double phaseMult = phaseMult(ai.getPhaseAttackMultipliers(), phase);
        if ("ENEMY_ALL".equals(skill.getTarget())) {
            double total = 0;
            for (BattleUnit target : targets) {
                total += estimateAttackValue(caster, target, skill, ai);
            }
            out.add(new Candidate(skill.getId(), null, total * phaseMult));
            return;
        }
        // ENEMY_SINGLE（及其他单体指向敌方的类型）
        for (BattleUnit target : targets) {
            double score = estimateAttackValue(caster, target, skill, ai) * phaseMult;
            out.add(new Candidate(skill.getId(), target.getUnitId(), score));
        }
    }

    /** 治疗候选：每个合法友方目标一个候选，评分自然选出 HP% 最低者。 */
    private void generateHealCandidates(List<Candidate> out, BattleUnit caster, SkillConfig skill,
                                        BattleSide ownSide, int phase, SystemRuleConfig.BossAiConfig ai) {
        double phaseMult = phaseMult(ai.getPhaseHealMultipliers(), phase);
        double healAmount = healCalculator.calculateHeal(caster, skill);
        switch (skill.getTarget()) {
            case "ALLY_ALL" -> {
                double total = 0;
                for (BattleUnit ally : ownSide.getActiveAliveUnits()) {
                    total += healValueOf(ally, healAmount, ai);
                }
                if (total > 0) {
                    out.add(new Candidate(skill.getId(), null, total * phaseMult));
                }
            }
            case "ALLY_SINGLE" -> {
                for (BattleUnit ally : ownSide.getActiveAliveUnits()) {
                    double value = healValueOf(ally, healAmount, ai);
                    if (value > 0) {
                        out.add(new Candidate(skill.getId(), ally.getUnitId(), value * phaseMult));
                    }
                }
            }
            default -> { // SELF
                double value = healValueOf(caster, healAmount, ai);
                if (value > 0) {
                    out.add(new Candidate(skill.getId(), caster.getUnitId(), value * phaseMult));
                }
            }
        }
    }

    /** 护盾候选：自身/友方单体；自身 HP 越低评分越高。 */
    private void generateShieldCandidates(List<Candidate> out, BattleUnit caster, SkillConfig skill,
                                          BattleSide ownSide, int phase, SystemRuleConfig.BossAiConfig ai) {
        double phaseMult = phaseMult(ai.getPhaseHealMultipliers(), phase);
        double shieldValue = healCalculator.calculateShield(caster, skill);
        List<BattleUnit> candidates = "ALLY_SINGLE".equals(skill.getTarget())
                ? ownSide.getActiveAliveUnits() : List.of(caster);
        for (BattleUnit target : candidates) {
            double hpPercent = (double) target.getCurrentHp() / target.getMaxHp();
            double score = shieldValue * (1.0 + (1.0 - hpPercent) * 0.5) * phaseMult;
            out.add(new Candidate(skill.getId(),
                    "ALLY_SINGLE".equals(skill.getTarget()) ? target.getUnitId() : caster.getUnitId(), score));
        }
    }

    /**
     * 状态类技能候选（effectType=NONE）：
     * 附加 SPECIAL_CONTROL 状态 → 控制技能评分；其余（减益/增益）→ 辅助技能评分。
     */
    private void generateStatusSkillCandidates(List<Candidate> out, BattleUnit caster, SkillConfig skill,
                                               BattleSide enemyOfSide, BattleSide ownSide, int phase,
                                               SystemRuleConfig.BossAiConfig ai) {
        SkillConfig.SkillEffectConfig controlEffect = findControlEffect(skill);
        double phaseMult = phaseMult(ai.getPhaseControlMultipliers(), phase);
        if ("ALLY_SINGLE".equals(skill.getTarget())) {
            // 友方增益：选未携带同名状态的友方
            SkillConfig.SkillEffectConfig effect = firstApplyStatusEffect(skill);
            if (effect == null) {
                return;
            }
            for (BattleUnit ally : ownSide.getActiveAliveUnits()) {
                double score = ai.getUtilityBaseScore() * effect.getChance() * phaseMult;
                if (effect.getStatusId() != null && ally.hasStatus(effect.getStatusId())) {
                    score *= ai.getExistingControlPenalty();
                }
                out.add(new Candidate(skill.getId(), ally.getUnitId(), score));
            }
            return;
        }
        // 敌方目标（ENEMY_SINGLE 等）
        for (BattleUnit target : enemyOfSide.getActiveAliveUnits()) {
            if (controlEffect != null) {
                out.add(new Candidate(skill.getId(), target.getUnitId(),
                        scoreControl(target, controlEffect, phaseMult, ai)));
            } else {
                SkillConfig.SkillEffectConfig effect = firstApplyStatusEffect(skill);
                double chance = effect != null ? effect.getChance() : 1.0;
                double score = ai.getUtilityBaseScore() * chance * phaseMult;
                if (effect != null && effect.getStatusId() != null && target.hasStatus(effect.getStatusId())) {
                    score *= ai.getExistingControlPenalty();
                }
                out.add(new Candidate(skill.getId(), target.getUnitId(), score));
            }
        }
    }

    // ---- 评分 ----

    /**
     * 单个攻击目标的价值估算：
     * 估算伤害（含克制/减伤/本属性加成）× 低血加权 + 斩杀奖励 + 附加状态效果估值。
     */
    private double estimateAttackValue(BattleUnit caster, BattleUnit target, SkillConfig skill,
                                       SystemRuleConfig.BossAiConfig ai) {
        double damage = estimateDamage(caster, target, skill);
        double hpPercent = (double) target.getCurrentHp() / target.getMaxHp();
        // 低血目标加权：HP% 越低评分越高，但不绝对（仍与伤害/克制共同竞争）
        double score = damage * (1.0 + ai.getLowHpTargetWeight() * (1.0 - hpPercent));
        // 斩杀奖励：预计伤害 ≥ 目标有效生命（HP + 护盾）时视为可击杀
        int effectiveHp = target.getCurrentHp() + target.getShield();
        if (damage >= effectiveHp) {
            score += damage * ai.getKillBonusPercent();
        }
        // 附加状态效果（DOT/减益等）的期望附加值
        for (SkillConfig.SkillEffectConfig effect : skill.getEffects()) {
            if ("APPLY_STATUS".equals(effect.getType()) || "STACK".equals(effect.getType())) {
                score += effect.getChance() * ai.getStatusEffectBonus() * damage;
            }
        }
        return score;
    }

    /**
     * 估算伤害（确定性，不含暴击与随机，不重新实现结算逻辑）：
     * 复用 DamageCalculator 基础值与减伤公式 + 现有克制表 + 本属性加成。
     */
    private double estimateDamage(BattleUnit caster, BattleUnit target, SkillConfig skill) {
        double base = DamageCalculator.computeBaseValue(skill, caster);
        SystemRuleConfig rules = registry.getSystemRules();
        double mitigated;
        if ("PHYSICAL".equalsIgnoreCase(skill.getDamageType())) {
            mitigated = DamageCalculator.mitigate(base, target.getDefense(), rules.getDefenseMitigationConstant());
        } else if ("MAGICAL".equalsIgnoreCase(skill.getDamageType())) {
            mitigated = DamageCalculator.mitigate(base, target.getResistance(), rules.getDefenseMitigationConstant());
        } else {
            mitigated = base;
        }
        double elementMult = 1.0;
        String skillElement = skill.getElement();
        if (skillElement != null && !"NONE".equalsIgnoreCase(skillElement)) {
            // 属性克制：读取现有克制表（克制 ×1.50 / 被克 ×0.75），不重新硬编码
            elementMult = registry.getElementAdvantageMultiplier(skillElement, target.getElement());
            if (skillElement.equalsIgnoreCase(caster.getElement())) {
                elementMult *= rules.getSameElementBonus();
            }
        }
        return mitigated * elementMult;
    }

    /** 治疗价值：过量部分不计分；低血紧迫加权；接近满血大幅降权。 */
    private double healValueOf(BattleUnit target, double healAmount, SystemRuleConfig.BossAiConfig ai) {
        int missing = target.getMaxHp() - target.getCurrentHp();
        if (missing <= 0) {
            return 0; // 满血不治疗
        }
        double effective = Math.min(healAmount, missing);
        double hpPercent = (double) target.getCurrentHp() / target.getMaxHp();
        double score = effective;
        if (hpPercent < ai.getHealTriggerHpPercent()) {
            score *= ai.getHealUrgencyMultiplier();
        } else if (hpPercent > ai.getHealNoNeedHpPercent()) {
            score *= 0.1; // 接近满血时不浪费治疗
        }
        return score;
    }

    /**
     * 控制技能评分：
     * 目标已受控 → 大幅降权（避免机械连续控制）；
     * 估算成功率复用引擎同公式：chance × controlResistance × consecutiveControlDecay（只估算，不改结算）。
     */
    private double scoreControl(BattleUnit target, SkillConfig.SkillEffectConfig controlEffect,
                                double phaseMult, SystemRuleConfig.BossAiConfig ai) {
        double score = ai.getControlBaseScore()
                * estimateControlChance(target, controlEffect.getChance())
                * phaseMult;
        if (hasControlStatus(target)) {
            score *= ai.getExistingControlPenalty();
        }
        return score;
    }

    /** 估算控制成功率（与 BattleEngine.computeFinalStatusChance 同公式，仅用于决策排序）。 */
    private double estimateControlChance(BattleUnit target, double baseChance) {
        SystemRuleConfig rules = registry.getSystemRules();
        double chance = baseChance * target.getControlResistance();
        List<Double> decay = rules.getConsecutiveControlDecay();
        if (decay != null && !decay.isEmpty()) {
            int count = target.getConsecutiveControlCount();
            chance *= count < decay.size() ? decay.get(count) : rules.getConsecutiveControlMin();
        }
        return Math.max(0, Math.min(1.0, chance));
    }

    // ---- 阶段与选择 ----

    /**
     * 当前阶段索引 = 已激活的阶段触发器数量（0 = 第一阶段）。
     * 阶段激活由引擎 checkPhaseTriggers 维护，AI 只读取，不重建阶段系统。
     */
    private int currentPhase(BattleUnit unit) {
        int activated = 0;
        if (unit.getPhaseActivated() != null) {
            for (Boolean flag : unit.getPhaseActivated()) {
                if (Boolean.TRUE.equals(flag)) {
                    activated++;
                }
            }
        }
        return activated;
    }

    /** 阶段倍率查询：索引越界取末项。 */
    private double phaseMult(List<Double> multipliers, int phase) {
        if (multipliers == null || multipliers.isEmpty()) {
            return 1.0;
        }
        return multipliers.get(Math.min(phase, multipliers.size() - 1));
    }

    /**
     * 选出最高评分候选；评分 ≥ 最高分 × (1 - tieTolerance) 的候选间
     * 用战斗统一随机选择（固定种子可复现），避免行为完全固定。
     */
    private Candidate pickBest(BattleContext ctx, List<Candidate> candidates, double tieTolerance) {
        double bestScore = Double.NEGATIVE_INFINITY;
        for (Candidate candidate : candidates) {
            bestScore = Math.max(bestScore, candidate.score);
        }
        double threshold = bestScore * (1.0 - Math.max(0, tieTolerance));
        List<Candidate> close = new ArrayList<>();
        for (Candidate candidate : candidates) {
            if (candidate.score >= threshold) {
                close.add(candidate);
            }
        }
        return close.size() == 1 ? close.get(0)
                : close.get(ctx.getRandom().nextInt(0, close.size() - 1));
    }

    // ---- 技能/状态识别辅助 ----

    /** 技能是否附加控制状态（SPECIAL_CONTROL 类别）。 */
    private SkillConfig.SkillEffectConfig findControlEffect(SkillConfig skill) {
        for (SkillConfig.SkillEffectConfig effect : skill.getEffects()) {
            if ("APPLY_STATUS".equals(effect.getType()) && isControlStatus(effect.getStatusId())) {
                return effect;
            }
        }
        return null;
    }

    /** 第一个 APPLY_STATUS 附加效果（减益/增益识别用）。 */
    private SkillConfig.SkillEffectConfig firstApplyStatusEffect(SkillConfig skill) {
        for (SkillConfig.SkillEffectConfig effect : skill.getEffects()) {
            if ("APPLY_STATUS".equals(effect.getType()) || "STACK".equals(effect.getType())) {
                return effect;
            }
        }
        return null;
    }

    /** 目标是否已处于任一控制状态（SPECIAL_CONTROL 类别）。 */
    private boolean hasControlStatus(BattleUnit target) {
        return target.getStatuses().stream()
                .anyMatch(s -> isControlStatus(s.getStatusId()));
    }

    /** 状态是否为控制类（读取现有状态配置分类，不硬编码状态 ID）。 */
    private boolean isControlStatus(String statusId) {
        if (statusId == null) {
            return false;
        }
        StatusEffectConfig config = registry.getStatus(statusId);
        return config != null && "SPECIAL_CONTROL".equals(config.getCategory());
    }

    /** 候选行动（技能 × 目标 × 评分）。 */
    private record Candidate(String skillId, String targetId, double score) {
    }
}
