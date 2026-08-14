package com.petgame.battle.ai;

import com.petgame.battle.calculator.CaptureCalculator;
import com.petgame.battle.calculator.HealCalculator;
import com.petgame.battle.calculator.StatusModifiers;
import com.petgame.battle.engine.BattleContext;
import com.petgame.battle.model.BattleAction;
import com.petgame.battle.model.BattleSide;
import com.petgame.battle.model.BattleUnit;
import com.petgame.config.GameConfigRegistry;
import com.petgame.config.model.ItemConfig;
import com.petgame.config.model.PassiveSkillConfig;
import com.petgame.config.model.PetSpeciesConfig;
import com.petgame.config.model.SkillConfig;
import com.petgame.config.model.StatusEffectConfig;
import com.petgame.config.model.SystemRuleConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 玩家侧自动战斗决策器（阶段 10 自动战斗策略系统，评分式规则 AI）。
 * <p>
 * 决策模型：统一候选行动生成（技能×目标 / 换宠 / 捕捉 / 道具 / 防御）
 * → 合法性过滤 → 基础评分（伤害/治疗/控制/捕捉等语义维度）
 * → 宠物定位修正 → 策略预设修正（BALANCED/AGGRESSIVE/DEFENSIVE/CAPTURE 权重表）
 * → 特殊规则修正（误杀风险/留生一击/1HP震慑/命运天平）→ 选择最高分
 * （接近分候选间用战斗统一随机小幅随机，固定种子可复现）。
 * <ul>
 *   <li>技能语义以 {@link SkillConfig#getTags()} AI 标签为准，无标签时按
 *       effectType/effects 结构推断，禁止按技能名称硬编码行为。</li>
 *   <li>AI 只做估算排序：伤害/克制/控制成功率/捕捉率的实际结算仍由 BattleEngine 负责。</li>
 *   <li>只返回合法行动；无任何候选时回退 DEFEND（引擎兜底），永不卡死。</li>
 *   <li>不读取玩家等级/战力做动态缩放，决策仅来源于配置与战场状态。</li>
 * </ul>
 * 详细设计见 docs/technical/AUTO_BATTLE_DESIGN.md。
 */
@Component
public class AutoBattleDecisionProvider implements DecisionProvider {

    private static final Logger log = LoggerFactory.getLogger(AutoBattleDecisionProvider.class);

    private final GameConfigRegistry registry;
    private final HealCalculator healCalculator;

    public AutoBattleDecisionProvider(GameConfigRegistry registry) {
        this.registry = registry;
        this.healCalculator = new HealCalculator(registry);
    }

    @Override
    public List<BattleAction> decide(BattleContext ctx, BattleSide side) {
        AutoBattleSettings settings = ctx.getAutoSettings() != null
                ? ctx.getAutoSettings() : new AutoBattleSettings();
        List<BattleAction> actions = new ArrayList<>();
        BattleSide enemyOfSide = ctx.getOpposite(side);
        for (BattleUnit unit : side.getActiveAliveUnits()) {
            BattleAction action = decideForUnit(ctx, unit, side, enemyOfSide, settings);
            actions.add(action);
            if (log.isDebugEnabled()) {
                log.debug("AutoBattle: strategy={} unit={} action={}:{}:{} ",
                        settings.getStrategy(), unit.getUnitId(), action.getType(),
                        action.getSkillId() != null ? action.getSkillId() : "",
                        action.getTargetId() != null ? action.getTargetId()
                                : (action.getSwitchPetId() != null ? action.getSwitchPetId() : ""));
            }
        }
        return actions;
    }

    /** 单个单位的决策：生成全部候选行动并选出最高分（接近分随机）。 */
    private BattleAction decideForUnit(BattleContext ctx, BattleUnit unit, BattleSide ownSide,
                                       BattleSide enemyOfSide, AutoBattleSettings settings) {
        SystemRuleConfig.AutoBattleConfig ai = registry.getSystemRules().getAutoBattle();
        String strategy = normalizeStrategy(settings.getStrategy());
        String role = resolveRole(unit);
        List<Candidate> candidates = new ArrayList<>();

        // 保底候选：防御（永不卡死）
        candidates.add(new Candidate(BattleAction.defend(unit.getUnitId()), null,
                ai.getDefendBaseScore(), "defend-fallback"));

        // 技能候选（沉默时无技能候选，仍可换宠/道具/捕捉/防御）
        boolean silenced = StatusModifiers.of(unit, registry.getStatusIndex()).isSilenced();
        if (!silenced) {
            for (String skillId : unit.getReadySkillIds()) {
                SkillConfig skill = registry.getSkill(skillId);
                if (skill == null) {
                    continue;
                }
                generateSkillCandidates(candidates, ctx, unit, skill, ownSide, enemyOfSide, settings, ai);
            }
        }

        // 换宠候选
        generateSwitchCandidates(candidates, ctx, unit, ownSide, enemyOfSide, settings, ai);
        // 捕捉候选
        generateCaptureCandidates(candidates, ctx, unit, enemyOfSide, settings, ai);
        // 道具候选（恢复/复苏，默认关闭）
        generateItemCandidates(candidates, ctx, unit, ownSide, enemyOfSide, settings, ai);

        // 定位修正 × 策略修正
        for (Candidate candidate : candidates) {
            applyRoleModifier(candidate, ai, role);
            applyStrategyModifier(candidate, ai, strategy, unit);
        }

        Candidate best = pickBest(ctx, candidates, ai.getTieTolerance());
        if (log.isDebugEnabled()) {
            log.debug("AutoBattle: chosen score={} reason={} candidates={}",
                    String.format("%.1f", best.score), best.reason, candidates.size());
        }
        return best.action;
    }

    // ==================== 候选生成 ====================

    /** 技能候选：按主效果类型分流（DAMAGE/HEAL/SHIELD/状态类/特殊交换）。 */
    private void generateSkillCandidates(List<Candidate> out, BattleContext ctx, BattleUnit caster,
                                          SkillConfig skill, BattleSide ownSide, BattleSide enemyOfSide,
                                          AutoBattleSettings settings, SystemRuleConfig.AutoBattleConfig ai) {
        Set<String> tags = resolveTags(skill);
        // 命运天平（HP_PERCENT_EXCHANGE）独立收益模型，不按普通标签给分
        if (hasEffect(skill, "HP_PERCENT_EXCHANGE")) {
            scoreHpExchange(out, ctx, caster, skill, enemyOfSide, ai);
            return;
        }
        String effectType = skill.getEffectType() != null ? skill.getEffectType().toUpperCase() : "";
        switch (effectType) {
            case "DAMAGE" -> generateAttackCandidates(out, ctx, caster, skill, enemyOfSide, tags, settings, ai);
            case "HEAL" -> generateHealCandidates(out, caster, skill, ownSide, settings, ai);
            case "SHIELD" -> generateShieldCandidates(out, caster, skill, ownSide, ai);
            default -> generateUtilityCandidates(out, ctx, caster, skill, ownSide, enemyOfSide, tags, settings, ai);
        }
    }

    /** 攻击候选：单体每个敌方目标一个候选；群体求和一个候选。伤害价值按有效生命封顶避免过杀虚高。 */
    private void generateAttackCandidates(List<Candidate> out, BattleContext ctx, BattleUnit caster,
                                          SkillConfig skill, BattleSide enemyOfSide, Set<String> tags,
                                          AutoBattleSettings settings, SystemRuleConfig.AutoBattleConfig ai) {
        List<BattleUnit> targets = enemyOfSide.getActiveAliveUnits();
        if (targets.isEmpty()) {
            return;
        }
        if ("ENEMY_ALL".equals(skill.getTarget())) {
            double total = 0;
            StringBuilder reasons = new StringBuilder("AOE");
            for (BattleUnit target : targets) {
                total += scoreAttack(ctx, caster, target, skill, tags, settings, ai, reasons);
            }
            if (total > 0) {
                out.add(new Candidate(BattleAction.skill(caster.getUnitId(), skill.getId(), null),
                        "DAMAGE", total, reasons.toString()));
            }
            return;
        }
        for (BattleUnit target : targets) {
            StringBuilder reasons = new StringBuilder();
            double score = scoreAttack(ctx, caster, target, skill, tags, settings, ai, reasons);
            if (score > 0) {
                out.add(new Candidate(BattleAction.skill(caster.getUnitId(), skill.getId(), target.getUnitId()),
                        "DAMAGE", score, reasons.toString()));
            }
        }
    }

    /**
     * 单个攻击候选评分：
     * 估算伤害（含克制）按目标有效生命封顶 → 低血加权 → 斩杀奖励 → FINISHER 加成
     * → 破盾加成 → 吸血恢复价值 → 高伤浪费惩罚 → 捕捉误杀风险/留生一击修正。
     */
    private double scoreAttack(BattleContext ctx, BattleUnit caster, BattleUnit target, SkillConfig skill,
                               Set<String> tags, AutoBattleSettings settings,
                               SystemRuleConfig.AutoBattleConfig ai, StringBuilder reasons) {
        double damage = BattleAiUtils.estimateDamage(registry, caster, target, skill);
        double hpPercent = BattleAiUtils.hpPercent(target);
        int effectiveHp = target.getCurrentHp() + target.getShield();
        if (effectiveHp <= 0) {
            return 0;
        }
        // 伤害价值按有效生命封顶：超出部分属于浪费，天然抑制高伤打残血
        double valued = Math.min(damage, effectiveHp);
        double score = valued * (1.0 + ai.getLowHpTargetWeight() * (1.0 - hpPercent));
        reasons.append("dmg=").append((int) damage).append(';');

        boolean killable = damage >= effectiveHp;
        if (killable) {
            double killBonus = effectiveHp * ai.getKillBonusPercent();
            // 攻击触发/队伍协同类被动（乘胜追击/复仇等）：AI 更偏好压低血目标以触发击杀强化（阶段 14，语义组驱动）
            if (passiveHasGroup(caster, "ON_KILL_ATK") || passiveHasGroup(caster, "ON_ALLY_DEFEAT_ATK")) {
                killBonus *= 1.2;
                reasons.append("killpassive+;");
            }
            score += killBonus;
            reasons.append("kill+;");
        }
        // FINISHER：目标低血时终结技能价值随 HP 下降线性上升
        if (tags.contains("FINISHER") && hpPercent <= ai.getFinisherHpThreshold()) {
            score += valued * 0.5 * (1.0 - hpPercent);
            reasons.append("finisher+;");
        }
        // SHIELD_BREAK：有护盾才有额外价值，无护盾不因标签滥放
        if (tags.contains("SHIELD_BREAK")) {
            if (target.getShield() > 0) {
                score += target.getShield() * 0.8;
                reasons.append("shieldbreak+;");
            } else {
                score *= 0.6;
                reasons.append("noshield-;");
            }
        }
        // LIFE_STEAL：攻击价值 + 按自身缺血程度的恢复价值（满血无额外加成）
        if (tags.contains("LIFE_STEAL")) {
            double percent = maxEffectPercent(skill, "LIFE_STEAL");
            double selfMissing = 1.0 - BattleAiUtils.hpPercent(caster);
            score += damage * percent * selfMissing;
            reasons.append("lifesteal+;");
        }
        // 高伤浪费惩罚：极低血目标 + 高冷却技能（进攻策略豁免）
        if (killable && hpPercent < ai.getOverkillLowHpPercent()
                && skill.getCooldown() >= ai.getHighCooldownThreshold()
                && !"AGGRESSIVE".equals(settings.getStrategy())) {
            score *= (1.0 - ai.getOverkillWastePenalty());
            reasons.append("overkillwaste-;");
        }
        // 冷却成本：小幅抑制高冷却技能频繁参选
        score *= (1.0 - 0.03 * skill.getCooldown());
        // 高威胁目标聚焦
        score *= (0.8 + 0.4 * BattleAiUtils.threatOf(target));

        // 捕捉策略修正：误杀风险惩罚 + 留生一击优先（无可用捕捉球时不进入捕捉模式）
        boolean captureMode = "CAPTURE".equals(settings.getStrategy())
                && isCapturableTarget(ctx, target) && hasCaptureBallAvailable(ctx);
        if (captureMode && hpPercent < ai.getCaptureDangerHp()) {
            boolean leaveAlive = hasEffect(skill, "LEAVE_AT_ONE_HP");
            if (killable && !leaveAlive) {
                // 误杀风险：击杀本身就是负面结果，去掉斩杀奖励与低血加权，仅保留大幅惩罚后的残余价值
                score = valued * (1.0 - ai.getCaptureKillPenalty());
                reasons.append("captureKillRisk-;");
            }
            if (leaveAlive || tags.contains("CAPTURE_ASSIST")) {
                score += ai.getCaptureAssistLeaveAliveBonus();
                reasons.append("captureAssist+;");
            }
        }
        return Math.max(0, score);
    }

    /** 治疗候选：过量不计分；三段 HP 阈值紧迫度倍率；接近满血大幅降权。 */
    private void generateHealCandidates(List<Candidate> out, BattleUnit caster, SkillConfig skill,
                                        BattleSide ownSide, AutoBattleSettings settings,
                                        SystemRuleConfig.AutoBattleConfig ai) {
        double healAmount = healCalculator.calculateHeal(caster, skill);
        switch (skill.getTarget()) {
            case "ALLY_ALL" -> {
                double total = 0;
                for (BattleUnit ally : ownSide.getActiveAliveUnits()) {
                    total += healValueOf(ally, healAmount, settings, ai);
                }
                if (total > 0) {
                    out.add(new Candidate(BattleAction.skill(caster.getUnitId(), skill.getId(), null),
                            "HEAL", total, "heal-all"));
                }
            }
            case "ALLY_SINGLE" -> {
                for (BattleUnit ally : ownSide.getActiveAliveUnits()) {
                    double value = healValueOf(ally, healAmount, settings, ai);
                    if (value > 0) {
                        out.add(new Candidate(BattleAction.skill(caster.getUnitId(), skill.getId(), ally.getUnitId()),
                                "HEAL", value, "heal:" + (int) value));
                    }
                }
            }
            default -> { // SELF
                double value = healValueOf(caster, healAmount, settings, ai);
                if (value > 0) {
                    out.add(new Candidate(BattleAction.skill(caster.getUnitId(), skill.getId(), caster.getUnitId()),
                            "HEAL", value, "heal-self:" + (int) value));
                }
            }
        }
    }

    /** 治疗价值：有效恢复量（过量不计）× 紧迫度；HP 高于免费阈值时近 0。 */
    private double healValueOf(BattleUnit target, double healAmount,
                               AutoBattleSettings settings, SystemRuleConfig.AutoBattleConfig ai) {
        int missing = target.getMaxHp() - target.getCurrentHp();
        if (missing <= 0) {
            return 0; // 满血不治疗
        }
        double effective = Math.min(healAmount, missing);
        double hpPercent = BattleAiUtils.hpPercent(target);
        if (hpPercent > ai.getHealNoNeedHpPercent()) {
            return effective * 0.1; // 接近满血不浪费大治疗
        }
        double score = effective;
        List<Double> thresholds = ai.getHealHpThresholds();
        List<Double> multipliers = ai.getHealUrgencyMultipliers();
        for (int i = 0; i < thresholds.size(); i++) {
            if (hpPercent < thresholds.get(i)) {
                score *= i < multipliers.size() ? multipliers.get(i) : 1.0;
                break;
            }
        }
        // 稳健策略提前恢复
        if ("DEFENSIVE".equals(settings.getStrategy()) && hpPercent < ai.getDefensiveHealEarly()) {
            score *= 1.3;
        }
        return score;
    }

    /** 护盾候选：自身/友方单体；自身越危险护盾越有价值。 */
    private void generateShieldCandidates(List<Candidate> out, BattleUnit caster, SkillConfig skill,
                                          BattleSide ownSide, SystemRuleConfig.AutoBattleConfig ai) {
        double shieldValue = healCalculator.calculateShield(caster, skill);
        List<BattleUnit> candidates = "ALLY_SINGLE".equals(skill.getTarget())
                ? ownSide.getActiveAliveUnits() : List.of(caster);
        for (BattleUnit target : candidates) {
            double hpPercent = BattleAiUtils.hpPercent(target);
            double score = shieldValue * (1.0 + (1.0 - hpPercent)) * 0.8;
            out.add(new Candidate(BattleAction.skill(caster.getUnitId(), skill.getId(),
                    "ALLY_SINGLE".equals(skill.getTarget()) ? target.getUnitId() : caster.getUnitId()),
                    "SURVIVAL", score, "shield"));
        }
    }

    /** 状态类技能候选（effectType=NONE）：控制/驱散/行动顺序/自保/增益分流评分。 */
    private void generateUtilityCandidates(List<Candidate> out, BattleContext ctx, BattleUnit caster,
                                           SkillConfig skill, BattleSide ownSide, BattleSide enemyOfSide,
                                           Set<String> tags, AutoBattleSettings settings,
                                           SystemRuleConfig.AutoBattleConfig ai) {
        SkillConfig.SkillEffectConfig controlEffect = findEffectOfStatusCategory(skill, "SPECIAL_CONTROL");
        SkillConfig.SkillEffectConfig dispelEffect = findEffect(skill, "DISPEL");

        // 友方指向：驱散己方负面 / 增益
        if ("ALLY_SINGLE".equals(skill.getTarget()) || "ALLY_ALL".equals(skill.getTarget())
                || "SELF".equals(skill.getTarget())) {
            if ("ALLY_ALL".equals(skill.getTarget())) {
                double total = scoreAllyUtility(caster, ownSide.getActiveAliveUnits(), skill, dispelEffect, ai);
                if (total > 0) {
                    out.add(new Candidate(BattleAction.skill(caster.getUnitId(), skill.getId(), null),
                            dispelEffect != null ? "DISPEL" : "SURVIVAL", total, "ally-all"));
                }
                return;
            }
            List<BattleUnit> allies = "SELF".equals(skill.getTarget())
                    ? List.of(caster) : ownSide.getActiveAliveUnits();
            for (BattleUnit ally : allies) {
                double score = scoreAllyUtilityOn(caster, ally, skill, dispelEffect, ai);
                if (score > 0) {
                    out.add(new Candidate(BattleAction.skill(caster.getUnitId(), skill.getId(), ally.getUnitId()),
                            dispelEffect != null ? "DISPEL" : "SURVIVAL", score, "ally-util"));
                }
            }
            return;
        }

        // 敌方指向：控制 / 驱散敌方增益 / 行动顺序 / 一般减益
        boolean actionOrder = hasEffect(skill, "CHANGE_ACTION_ORDER");
        for (BattleUnit target : enemyOfSide.getActiveAliveUnits()) {
            double score;
            String tag;
            String reason;
            if (controlEffect != null) {
                score = scoreControl(ctx, caster, target, skill, controlEffect, tags, settings, ai);
                tag = "CONTROL";
                reason = "control";
            } else if (dispelEffect != null) {
                score = scoreDispelEnemy(target, dispelEffect, ai);
                tag = "DISPEL";
                reason = "dispel";
            } else if (actionOrder) {
                score = scoreActionOrder(caster, target, enemyOfSide, ai);
                tag = "ACTION_ORDER";
                reason = "order";
            } else if (hasEffect(skill, "PROTECT_FROM_DEFEAT")) {
                // 保命类对敌方目标无意义，跳过
                continue;
            } else {
                // 一般减益：基础分 × 概率 × 威胁
                SkillConfig.SkillEffectConfig effect = firstApplyStatusEffect(skill);
                double chance = effect != null ? effect.getChance() : 1.0;
                score = ai.getUtilityBaseScore() * chance * (0.7 + 0.6 * BattleAiUtils.threatOf(target));
                if (effect != null && effect.getStatusId() != null && target.hasStatus(effect.getStatusId())) {
                    score *= ai.getExistingControlPenalty();
                }
                tag = "CONTROL";
                reason = "debuff";
            }
            if (score > 0) {
                out.add(new Candidate(BattleAction.skill(caster.getUnitId(), skill.getId(), target.getUnitId()),
                        tag, score, reason));
            }
        }

        // 保命技能（PROTECT_FROM_DEFEAT）：自身危险时收益高
        if (hasEffect(skill, "PROTECT_FROM_DEFEAT")) {
            double selfMissing = 1.0 - BattleAiUtils.hpPercent(caster);
            int enemyAlive = enemyOfSide.getActiveAliveUnits().size();
            double score = ai.getUtilityBaseScore() * selfMissing * (1.0 + enemyAlive * 0.2);
            out.add(new Candidate(BattleAction.skill(caster.getUnitId(), skill.getId(), caster.getUnitId()),
                    "SURVIVAL", score, "protect"));
        }
    }

    /** 控制技能评分：成功率估算（抗性×连续衰减）× 威胁；已受控大幅降权；捕捉辅助场景加权。 */
    private double scoreControl(BattleContext ctx, BattleUnit caster, BattleUnit target, SkillConfig skill,
                                SkillConfig.SkillEffectConfig controlEffect, Set<String> tags,
                                AutoBattleSettings settings, SystemRuleConfig.AutoBattleConfig ai) {
        double chance = BattleAiUtils.estimateControlChance(registry, target, controlEffect.getChance());
        double score = ai.getControlBaseScore() * chance * (0.7 + 0.6 * BattleAiUtils.threatOf(target));
        if (BattleAiUtils.hasControlStatus(registry, target)) {
            score *= ai.getExistingControlPenalty(); // 不机械连续控制同一目标
        }
        // 捕捉策略下控制 = 捕捉辅助（异常加成 + 安全窗口）
        if (tags.contains("CAPTURE_ASSIST") && "CAPTURE".equals(settings.getStrategy())
                && isCapturableTarget(ctx, target)) {
            score *= 1.5;
        }
        return score;
    }

    /** 驱散敌方增益：敌方 BUFF 数量决定价值，无可驱散时近 0。 */
    private double scoreDispelEnemy(BattleUnit target, SkillConfig.SkillEffectConfig dispelEffect,
                                    SystemRuleConfig.AutoBattleConfig ai) {
        int buffCount = countStatusesOfCategory(target, "BUFF");
        double score = ai.getUtilityBaseScore() * dispelEffect.getChance() * buffCount;
        return buffCount == 0 ? score * 0.05 : score;
    }

    /** 友方驱散/增益评分：己方 DEBUFF/CONTINUOUS 数量决定驱散价值；增益按概率基础分。 */
    private double scoreAllyUtility(BattleUnit caster, List<BattleUnit> allies, SkillConfig skill,
                                    SkillConfig.SkillEffectConfig dispelEffect,
                                    SystemRuleConfig.AutoBattleConfig ai) {
        double total = 0;
        for (BattleUnit ally : allies) {
            total += scoreAllyUtilityOn(caster, ally, skill, dispelEffect, ai);
        }
        return total;
    }

    private double scoreAllyUtilityOn(BattleUnit caster, BattleUnit ally, SkillConfig skill,
                                      SkillConfig.SkillEffectConfig dispelEffect,
                                      SystemRuleConfig.AutoBattleConfig ai) {
        if (dispelEffect != null) {
            int debuffCount = countStatusesOfCategory(ally, "DEBUFF")
                    + countStatusesOfCategory(ally, "CONTINUOUS");
            double score = ai.getUtilityBaseScore() * dispelEffect.getChance() * debuffCount;
            return debuffCount == 0 ? score * 0.05 : score;
        }
        SkillConfig.SkillEffectConfig effect = firstApplyStatusEffect(skill);
        double chance = effect != null ? effect.getChance() : 1.0;
        double score = ai.getUtilityBaseScore() * chance * 0.8;
        if (effect != null && effect.getStatusId() != null && ally.hasStatus(effect.getStatusId())) {
            score *= 0.3; // 已有同类增益不重复施加
        }
        return score;
    }

    /** 行动顺序技能：敌方有速度更快的威胁单位时价值高，否则低分避免无脑使用。 */
    private double scoreActionOrder(BattleUnit caster, BattleUnit target, BattleSide enemyOfSide,
                                    SystemRuleConfig.AutoBattleConfig ai) {
        boolean fasterEnemyExists = enemyOfSide.getActiveAliveUnits().stream()
                .anyMatch(e -> e.getSpeed() > caster.getSpeed());
        double score = ai.getUtilityBaseScore() * 0.8;
        if (fasterEnemyExists) {
            score *= 1.5;
        }
        return score;
    }

    /**
     * 命运天平（HP_PERCENT_EXCHANGE）收益模型：
     * netBenefit = 自身 HP% 收益 − 敌方 HP% 收益；只有净收益达到阈值才生成候选；
     * Boss 目标使用更高阈值，避免局部收益给 Boss 大量回血。
     */
    private void scoreHpExchange(List<Candidate> out, BattleContext ctx, BattleUnit caster, SkillConfig skill,
                                 BattleSide enemyOfSide, SystemRuleConfig.AutoBattleConfig ai) {
        if (!"ENEMY_SINGLE".equals(skill.getTarget())) {
            return;
        }
        double selfPct = BattleAiUtils.hpPercent(caster);
        for (BattleUnit target : enemyOfSide.getActiveAliveUnits()) {
            double targetPct = BattleAiUtils.hpPercent(target);
            double selfGain = targetPct - selfPct;      // 交换后自身 HP% 变化
            double enemyGain = selfPct - targetPct;     // 交换后目标 HP% 变化
            double netBenefit = selfGain - enemyGain;
            boolean bossTarget = "BOSS".equals(ctx.getBattleType()) || ctx.isUncapturable();
            double threshold = bossTarget ? ai.getBalanceBossMinBenefit() : ai.getBalanceMinBenefit();
            if (netBenefit < threshold) {
                continue; // 小收益/帮敌人回血的交换不做
            }
            double score = netBenefit * 150.0;
            out.add(new Candidate(BattleAction.skill(caster.getUnitId(), skill.getId(), target.getUnitId()),
                    "SURVIVAL", score, "hpExchange:net=" + String.format("%.2f", netBenefit)));
        }
    }

    /** 换宠候选：阈值/被克制触发；候补按 HP%、属性适配评分，不优先残血候补。 */
    private void generateSwitchCandidates(List<Candidate> out, BattleContext ctx, BattleUnit unit,
                                          BattleSide ownSide, BattleSide enemyOfSide,
                                          AutoBattleSettings settings, SystemRuleConfig.AutoBattleConfig ai) {
        if (!settings.isAutoSwitch()) {
            return;
        }
        List<BattleUnit> bench = ownSide.getBenchAliveUnits();
        if (bench.isEmpty()) {
            return;
        }
        double threshold = settings.getAutoSwitchHpThreshold();
        String strategy = normalizeStrategy(settings.getStrategy());
        if ("DEFENSIVE".equals(strategy)) {
            threshold += 0.10; // 稳健更早换宠
        } else if ("AGGRESSIVE".equals(strategy)) {
            threshold *= 0.6;  // 进攻不轻易换宠
        }
        double hpPercent = BattleAiUtils.hpPercent(unit);
        boolean countered = enemyOfSide.getActiveAliveUnits().stream()
                .anyMatch(e -> registry.getElementAdvantageMultiplier(e.getElement(), unit.getElement()) > 1.0);
        boolean triggered = hpPercent < threshold || (countered && hpPercent < 0.5);
        if (!triggered) {
            return;
        }
        for (BattleUnit benchUnit : bench) {
            double benchHp = BattleAiUtils.hpPercent(benchUnit);
            double elementFit = 0.5;
            for (BattleUnit enemy : enemyOfSide.getActiveAliveUnits()) {
                double mult = registry.getElementAdvantageMultiplier(benchUnit.getElement(), enemy.getElement());
                if (mult > 1.0) {
                    elementFit = 1.0;
                    break;
                } else if (mult < 1.0) {
                    elementFit = 0.2;
                }
            }
            double score = benchHp * 40.0 + elementFit * 30.0 + 10.0;
            if (benchHp < 0.2) {
                score *= 0.3; // 不优先换上残血宠物（除非别无选择，评分自然排序）
            }
            out.add(new Candidate(BattleAction.switchPet(unit.getUnitId(), benchUnit.getUnitId()),
                    "SWITCH_ACTION", score, "switch:" + benchUnit.getUnitId()));
        }
    }

    /** 捕捉候选：仅野生可捕捉战斗；目标 1HP+震慑时巨额加成优先捕捉。 */
    private void generateCaptureCandidates(List<Candidate> out, BattleContext ctx, BattleUnit caster,
                                          BattleSide enemyOfSide, AutoBattleSettings settings,
                                          SystemRuleConfig.AutoBattleConfig ai) {
        if (ctx.isUncapturable()) {
            return;
        }
        List<BattleUnit> capturable = enemyOfSide.getActiveAliveUnits().stream()
                .filter(u -> !u.isCaptured() && u.getWildData() != null)
                .toList();
        if (capturable.isEmpty()) {
            return;
        }
        // 目标选择：指定捕捉目标优先，否则最低 HP% 可捕捉敌人
        BattleUnit target = null;
        if (settings.getCaptureTargetId() != null) {
            target = capturable.stream()
                    .filter(u -> u.getUnitId().equals(settings.getCaptureTargetId()))
                    .findFirst().orElse(null);
        }
        if (target == null) {
            target = capturable.stream()
                    .min((a, b) -> Double.compare(BattleAiUtils.hpPercent(a), BattleAiUtils.hpPercent(b)))
                    .orElse(null);
        }
        if (target == null) {
            return;
        }
        PetSpeciesConfig species = registry.getSpecies(target.getSpeciesId());
        if (species == null) {
            return;
        }
        double hpRatio = BattleAiUtils.hpPercent(target);
        int statusCount = CaptureCalculator.countCaptureBonusStatuses(target, registry.getStatusIndex());
        double eliteMultiplier = target.getWildData().isElite()
                ? registry.getSystemRules().getEliteCaptureMultiplier() : 1.0;

        for (Map.Entry<String, Integer> entry : ctx.getAvailableCaptureBalls().entrySet()) {
            int remaining = entry.getValue()
                    - ctx.getConsumedCaptureBalls().getOrDefault(entry.getKey(), 0);
            if (remaining <= 0) {
                continue;
            }
            ItemConfig ball = registry.getItem(entry.getKey());
            if (ball == null) {
                continue;
            }
            double rate = CaptureCalculator.computeCaptureRate(species.getCaptureRate(), hpRatio,
                    statusCount, ball.getValue(), eliteMultiplier, registry.getSystemRules());
            boolean readyToCapture = target.getCurrentHp() == 1
                    && BattleAiUtils.hasCaptureStun(registry, target);
            // 捕捉率低于门槛时继续削弱而非盲目投球（1HP+震慑时不受门槛限制）
            if (!readyToCapture && rate < ai.getCaptureMinRate()) {
                continue;
            }
            double score = rate * ai.getCaptureRateScoreFactor();
            String reason = "capture:rate=" + String.format("%.2f", rate);
            if (readyToCapture) {
                score += ai.getCaptureReadyBonus(); // 1 HP + 震慑：优先捕捉而不是继续攻击
                reason += ";ready";
            }
            out.add(new Candidate(BattleAction.capture(caster.getUnitId(), ball.getId(), target.getUnitId()),
                    "CAPTURE_ACTION", score, reason));
        }
    }

    /** 道具候选：恢复（默认关）与复苏（默认关），仅开关开启时生成。 */
    private void generateItemCandidates(List<Candidate> out, BattleContext ctx, BattleUnit caster,
                                        BattleSide ownSide, BattleSide enemyOfSide,
                                        AutoBattleSettings settings, SystemRuleConfig.AutoBattleConfig ai) {
        // 恢复道具：友方 HP 低于阈值时选择最小有效方案
        if (settings.isAutoUseRecoveryItem()) {
            for (BattleUnit ally : ownSide.getActiveAliveUnits()) {
                double hpPercent = BattleAiUtils.hpPercent(ally);
                if (hpPercent >= settings.getAutoRecoveryHpThreshold()) {
                    continue;
                }
                int missing = ally.getMaxHp() - ally.getCurrentHp();
                for (Map.Entry<String, Integer> entry : ctx.getAvailableRecoveryItems().entrySet()) {
                    ItemConfig item = registry.getItem(entry.getKey());
                    if (item == null || !"HEAL_HP".equals(item.getItemType())) {
                        continue;
                    }
                    int remaining = entry.getValue()
                            - ctx.getConsumedRecoveryItems().getOrDefault(entry.getKey(), 0);
                    if (remaining <= 0) {
                        continue;
                    }
                    double effective = Math.min(item.getValue(), missing);
                    double waste = Math.max(0, item.getValue() - missing);
                    double score = effective - waste * 0.3; // 最小有效方案优先，避免大药浪费
                    if (score > 0) {
                        out.add(new Candidate(itemAction(caster.getUnitId(), item.getId(), ally.getUnitId()),
                                "HEAL", score, "item-heal:" + item.getId()));
                    }
                }
            }
        }
        // 复苏道具：局势仍危险且非临门斩杀时才考虑
        if (settings.isAutoRevive()) {
            List<BattleUnit> deadUnits = ownSide.getUnits().stream()
                    .filter(u -> !u.isAlive() && !u.isCaptured()).toList();
            if (!deadUnits.isEmpty()) {
                List<BattleUnit> enemies = enemyOfSide.getActiveAliveUnits();
                boolean enemyNearlyDead = enemies.stream()
                        .anyMatch(e -> BattleAiUtils.hpPercent(e) < ai.getReviveEnemyNearlyDeadPercent());
                int totalEnemies = enemyOfSide.getUnits().size();
                double enemyRatio = totalEnemies > 0 ? (double) enemies.size() / totalEnemies : 0;
                int aliveActive = ownSide.getActiveAliveUnits().size();
                boolean dangerous = aliveActive <= 1 || enemyRatio >= ai.getReviveDangerEnemyRatio();
                if (!enemyNearlyDead && dangerous) {
                    for (BattleUnit dead : deadUnits) {
                        for (Map.Entry<String, Integer> entry : ctx.getAvailableRecoveryItems().entrySet()) {
                            ItemConfig item = registry.getItem(entry.getKey());
                            if (item == null || !"REVIVE".equals(item.getItemType())) {
                                continue;
                            }
                            int remaining = entry.getValue()
                                    - ctx.getConsumedRecoveryItems().getOrDefault(entry.getKey(), 0);
                            if (remaining <= 0) {
                                continue;
                            }
                            double revivedHp = dead.getMaxHp() * item.getValue();
                            double score = revivedHp * 0.5 + 20.0;
                            out.add(new Candidate(itemAction(caster.getUnitId(), item.getId(), dead.getUnitId()),
                                    "SURVIVAL", score, "item-revive:" + item.getId()));
                        }
                    }
                }
            }
        }
    }

    private static BattleAction itemAction(String petId, String itemId, String targetId) {
        BattleAction action = new BattleAction();
        action.setType("ITEM");
        action.setPetId(petId);
        action.setItemId(itemId);
        action.setTargetId(targetId);
        return action;
    }

    // ==================== 定位 / 权重修正 ====================

    /** 宠物定位：种族配置优先，未配置按技能组合与耐久/输出属性推断。 */
    private String resolveRole(BattleUnit unit) {
        PetSpeciesConfig species = registry.getSpecies(unit.getSpeciesId());
        if (species != null && species.getRole() != null && !species.getRole().isBlank()) {
            return species.getRole().toUpperCase();
        }
        boolean hasHeal = false;
        int controlSkills = 0;
        int total = 0;
        for (String skillId : unit.getSkillIds()) {
            SkillConfig skill = registry.getSkill(skillId);
            if (skill == null) {
                continue;
            }
            total++;
            if ("HEAL".equalsIgnoreCase(skill.getEffectType())) {
                hasHeal = true;
            }
            if (findEffectOfStatusCategory(skill, "SPECIAL_CONTROL") != null) {
                controlSkills++;
            }
        }
        if (hasHeal) {
            return "SUPPORT";
        }
        if (total > 0 && controlSkills * 2 >= total) {
            return "CONTROL";
        }
        double bulk = unit.getMaxHp() / 100.0 + unit.getDefense() / 50.0 + unit.getResistance() / 50.0;
        double attack = unit.getStrength() / 50.0 + unit.getSpirit() / 50.0;
        return bulk > attack * 1.2 ? "TANK" : "DAMAGE";
    }

    /** 定位修正：定位 × 语义标签权重（缺省 1.0，定位只影响倾向不禁止行为）。 */
    private void applyRoleModifier(Candidate candidate, SystemRuleConfig.AutoBattleConfig ai, String role) {
        if (candidate.tag == null) {
            return;
        }
        candidate.score *= weightOf(ai.getRoleWeights(), role, candidate.tag);
    }

    /** 策略修正：策略 × 语义标签权重；进攻策略低血时生存权重回升（生存底线）。 */
    private void applyStrategyModifier(Candidate candidate, SystemRuleConfig.AutoBattleConfig ai,
                                       String strategy, BattleUnit unit) {
        if (candidate.tag == null) {
            return;
        }
        double weight = weightOf(ai.getStrategyWeights(), strategy, candidate.tag);
        if ("AGGRESSIVE".equals(strategy) && BattleAiUtils.hpPercent(unit) < ai.getAggressiveSurvivalFloor()
                && ("HEAL".equals(candidate.tag) || "SURVIVAL".equals(candidate.tag)
                    || "SWITCH_ACTION".equals(candidate.tag))) {
            weight = Math.max(weight, 1.0); // 进攻策略不完全无视死亡风险
        }
        candidate.score *= weight;
    }

    private static double weightOf(Map<String, Map<String, Double>> table, String row, String tag) {
        if (table == null || row == null || tag == null) {
            return 1.0;
        }
        Map<String, Double> rowMap = table.get(row.toUpperCase());
        if (rowMap == null) {
            return 1.0;
        }
        return rowMap.getOrDefault(tag.toUpperCase(), 1.0);
    }

    /** 接近分随机：评分 ≥ 最高分 × (1 - tieTolerance) 的候选间用战斗统一随机选择。 */
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

    // ==================== 技能/状态识别辅助 ====================

    /**
     * 解析技能语义标签：配置 tags 优先，缺失时按 effectType/effects 结构推断。
     * 不按技能名称硬编码行为。
     */
    private Set<String> resolveTags(SkillConfig skill) {
        Set<String> tags = new LinkedHashSet<>();
        if (skill.getTags() != null) {
            for (String tag : skill.getTags()) {
                if (tag != null && !tag.isBlank()) {
                    tags.add(tag.trim().toUpperCase());
                }
            }
        }
        // 结构推断兜底
        String effectType = skill.getEffectType() != null ? skill.getEffectType().toUpperCase() : "";
        switch (effectType) {
            case "DAMAGE" -> tags.add("DAMAGE");
            case "HEAL" -> tags.add("HEAL");
            case "SHIELD" -> tags.add("SURVIVAL");
            default -> { }
        }
        for (SkillConfig.SkillEffectConfig effect : skill.getEffects()) {
            String type = effect.getType() != null ? effect.getType().toUpperCase() : "";
            switch (type) {
                case "LIFE_STEAL" -> tags.add("LIFE_STEAL");
                case "LEAVE_AT_ONE_HP" -> tags.add("CAPTURE_ASSIST");
                case "CHANGE_ACTION_ORDER" -> tags.add("ACTION_ORDER");
                case "DISPEL" -> tags.add("DISPEL");
                case "PROTECT_FROM_DEFEAT" -> tags.add("SURVIVAL");
                case "APPLY_STATUS" -> {
                    if (BattleAiUtils.isControlStatus(registry, effect.getStatusId())) {
                        tags.add("CONTROL");
                    }
                }
                default -> { }
            }
        }
        return tags;
    }

    /** 技能是否含指定类型效果。 */
    private boolean hasEffect(SkillConfig skill, String effectType) {
        return findEffect(skill, effectType) != null;
    }

    /**
     * 单位当前生效被动（含技能书被动）是否标注了指定效果组（语义组驱动，阶段 14）。
     * <p>
     * AI 通过 effectGroup 感知已启用的技能书被动，避免按技能书 ID 硬编码。
     */
    private boolean passiveHasGroup(BattleUnit unit, String effectGroup) {
        if (unit == null || unit.getPassives() == null || effectGroup == null) {
            return false;
        }
        for (PassiveSkillConfig p : unit.getPassives()) {
            if (effectGroup.equalsIgnoreCase(p.getEffectGroup())) {
                return true;
            }
        }
        return false;
    }

    private SkillConfig.SkillEffectConfig findEffect(SkillConfig skill, String effectType) {
        for (SkillConfig.SkillEffectConfig effect : skill.getEffects()) {
            if (effectType.equalsIgnoreCase(effect.getType())) {
                return effect;
            }
        }
        return null;
    }

    /** 指定效果类型的最大 percent 参数（LIFE_STEAL 吸血比例等）。 */
    private double maxEffectPercent(SkillConfig skill, String effectType) {
        double max = 0;
        for (SkillConfig.SkillEffectConfig effect : skill.getEffects()) {
            if (effectType.equalsIgnoreCase(effect.getType())) {
                max = Math.max(max, effect.getPercent());
            }
        }
        return max;
    }

    /** 技能是否附加指定类别状态（如 SPECIAL_CONTROL 控制）。 */
    private SkillConfig.SkillEffectConfig findEffectOfStatusCategory(SkillConfig skill, String category) {
        for (SkillConfig.SkillEffectConfig effect : skill.getEffects()) {
            if ("APPLY_STATUS".equals(effect.getType()) && isStatusOfCategory(effect.getStatusId(), category)) {
                return effect;
            }
        }
        return null;
    }

    private boolean isStatusOfCategory(String statusId, String category) {
        if (statusId == null) {
            return false;
        }
        StatusEffectConfig config = registry.getStatus(statusId);
        return config != null && category.equals(config.getCategory());
    }

    /** 目标携带的指定类别状态数量。 */
    private int countStatusesOfCategory(BattleUnit unit, String category) {
        int count = 0;
        for (var status : unit.getStatuses()) {
            if (isStatusOfCategory(status.getStatusId(), category)) {
                count++;
            }
        }
        return count;
    }

    private SkillConfig.SkillEffectConfig firstApplyStatusEffect(SkillConfig skill) {
        for (SkillConfig.SkillEffectConfig effect : skill.getEffects()) {
            if ("APPLY_STATUS".equals(effect.getType()) || "STACK".equals(effect.getType())) {
                return effect;
            }
        }
        return null;
    }

    /** 目标是否可捕捉（野生单位且战斗允许捕捉）。 */
    private boolean isCapturableTarget(BattleContext ctx, BattleUnit target) {
        return !ctx.isUncapturable() && target.getWildData() != null && !target.isCaptured();
    }

    /** 是否存在可用捕捉球（快照存量 − 本场已消耗）。 */
    private boolean hasCaptureBallAvailable(BattleContext ctx) {
        for (Map.Entry<String, Integer> entry : ctx.getAvailableCaptureBalls().entrySet()) {
            int remaining = entry.getValue()
                    - ctx.getConsumedCaptureBalls().getOrDefault(entry.getKey(), 0);
            if (remaining > 0) {
                return true;
            }
        }
        return false;
    }

    private static String normalizeStrategy(String strategy) {
        if (strategy == null) {
            return "BALANCED";
        }
        String upper = strategy.toUpperCase();
        return switch (upper) {
            case "AGGRESSIVE", "DEFENSIVE", "CAPTURE" -> upper;
            default -> "BALANCED";
        };
    }

    /** 候选行动（行动 × 语义标签 × 评分 × 调试理由）。 */
    private static class Candidate {
        final BattleAction action;
        final String tag;
        double score;
        final String reason;

        Candidate(BattleAction action, String tag, double score, String reason) {
            this.action = action;
            this.tag = tag;
            this.score = score;
            this.reason = reason;
        }
    }
}
