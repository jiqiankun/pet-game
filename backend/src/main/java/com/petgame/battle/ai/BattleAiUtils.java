package com.petgame.battle.ai;

import com.petgame.battle.calculator.DamageCalculator;
import com.petgame.battle.model.BattleUnit;
import com.petgame.config.GameConfigRegistry;
import com.petgame.config.model.SkillConfig;
import com.petgame.config.model.StatusEffectConfig;
import com.petgame.config.model.SystemRuleConfig;

import java.util.List;

/**
 * 战斗 AI 公共估算工具（阶段 10 自动战斗策略系统）。
 * <p>
 * 仅提供「决策排序」用途的确定性估算，不重新实现战斗结算逻辑：
 * 伤害估算复用 {@link DamageCalculator} 与现有克制表，控制概率估算
 * 与引擎 computeFinalStatusChance 同公式。实际结算仍由 BattleEngine 负责。
 */
public final class BattleAiUtils {

    private BattleAiUtils() {
    }

    /** 单位 HP 百分比（maxHp=0 时返回 0）。 */
    public static double hpPercent(BattleUnit unit) {
        return unit.getMaxHp() > 0 ? (double) unit.getCurrentHp() / unit.getMaxHp() : 0.0;
    }

    /**
     * 估算伤害（确定性，不含暴击与随机）：
     * 复用 DamageCalculator 基础值与减伤公式 + 现有克制表 + 本属性加成。
     */
    public static double estimateDamage(GameConfigRegistry registry, BattleUnit caster,
                                        BattleUnit target, SkillConfig skill) {
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
            elementMult = registry.getElementAdvantageMultiplier(skillElement, target.getElement());
            if (skillElement.equalsIgnoreCase(caster.getElement())) {
                elementMult *= rules.getSameElementBonus();
            }
        }
        return mitigated * elementMult;
    }

    /** 估算控制成功率（与 BattleEngine.computeFinalStatusChance 同公式，仅用于决策排序）。 */
    public static double estimateControlChance(GameConfigRegistry registry, BattleUnit target, double baseChance) {
        SystemRuleConfig rules = registry.getSystemRules();
        double chance = baseChance * target.getControlResistance();
        List<Double> decay = rules.getConsecutiveControlDecay();
        if (decay != null && !decay.isEmpty()) {
            int count = target.getConsecutiveControlCount();
            chance *= count < decay.size() ? decay.get(count) : rules.getConsecutiveControlMin();
        }
        return Math.max(0, Math.min(1.0, chance));
    }

    /** 目标是否已处于任一控制状态（SPECIAL_CONTROL 类别）。 */
    public static boolean hasControlStatus(GameConfigRegistry registry, BattleUnit target) {
        return target.getStatuses().stream()
                .anyMatch(s -> isControlStatus(registry, s.getStatusId()));
    }

    /** 状态是否为控制类（读取现有状态配置分类，不硬编码状态 ID）。 */
    public static boolean isControlStatus(GameConfigRegistry registry, String statusId) {
        if (statusId == null) {
            return false;
        }
        StatusEffectConfig config = registry.getStatus(statusId);
        return config != null && "SPECIAL_CONTROL".equals(config.getCategory());
    }

    /** 状态是否为捕获震慑（captureStun，安全捕捉窗口）。 */
    public static boolean hasCaptureStun(GameConfigRegistry registry, BattleUnit target) {
        return target.getStatuses().stream().anyMatch(s -> {
            StatusEffectConfig config = registry.getStatus(s.getStatusId());
            return config != null && config.isCaptureStun();
        });
    }

    /** 目标威胁度粗估 [0, 1]：力量与灵力的归一化（控制目标优先级用）。 */
    public static double threatOf(BattleUnit unit) {
        double raw = (unit.getStrength() + unit.getSpirit()) / 200.0;
        return Math.max(0.2, Math.min(1.0, raw));
    }
}
