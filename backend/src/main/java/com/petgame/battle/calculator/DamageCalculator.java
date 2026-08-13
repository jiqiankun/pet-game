package com.petgame.battle.calculator;

import com.petgame.battle.model.BattleUnit;
import com.petgame.common.GameRandom;
import com.petgame.config.GameConfigRegistry;
import com.petgame.config.model.SkillConfig;
import com.petgame.config.model.StatusesConfig;
import com.petgame.config.model.SystemRuleConfig;
import lombok.Getter;

import java.util.Map;

/**
 * 伤害计算器（阶段 3 核心结算链路）。
 * <p>
 * 结算顺序（需求文档 §41）：
 * <pre>
 * 技能基础值 + 属性系数
 *   ↓ 防御/抗性减伤
 *   ↓ 属性克制（×1.50 / ×0.75）
 *   ↓ 本属性加成（×1.20）
 *   ↓ 状态联动 + Buff/Debuff
 *   ↓ 暴击（1.4～2.0 均匀随机）
 *   ↓ 最终伤害（正常命中最低 1 点）
 * </pre>
 * 护盾被攻击时不再次套用防御计算，避免双重减伤（需求文档 §42）。
 */
public class DamageCalculator {

    private final GameConfigRegistry registry;

    public DamageCalculator(GameConfigRegistry registry) {
        this.registry = registry;
    }

    /**
     * 技能基础值 = baseValue + Σ(属性系数 × 对应属性)。
     * 支持多属性系数组合（如 0.8×力量 + 0.6×速度）。
     */
    public static double computeBaseValue(SkillConfig skill, BattleUnit caster) {
        return computeBaseValue(skill.getBaseValue(), skill.getScaling(), caster);
    }

    /**
     * 通用基础值计算（技能主效果与附加数值效果共用）。
     */
    public static double computeBaseValue(double baseValue, Map<String, Double> scaling, BattleUnit caster) {
        double value = baseValue;
        if (scaling != null) {
            for (Map.Entry<String, Double> entry : scaling.entrySet()) {
                value += entry.getValue() * getStat(caster, entry.getKey());
            }
        }
        return value;
    }

    /** 读取单位六维属性（scaling key 大写）。 */
    public static double getStat(BattleUnit unit, String statKey) {
        return switch (statKey.toUpperCase()) {
            case "HP" -> unit.getMaxHp();
            case "STRENGTH" -> unit.getStrength();
            case "SPIRIT" -> unit.getSpirit();
            case "DEFENSE" -> unit.getDefense();
            case "RESISTANCE" -> unit.getResistance();
            case "SPEED" -> unit.getSpeed();
            default -> throw new IllegalArgumentException("未知属性系数 key: " + statKey);
        };
    }

    /**
     * 防御/抗性减伤：raw × K / (K + def)，K 与 def 均为有效值。
     */
    public static double mitigate(double raw, double effectiveDefense, double constant) {
        return raw * constant / (constant + Math.max(0, effectiveDefense));
    }

    /**
     * 完整伤害结算。
     *
     * @param attacker    攻击方
     * @param defender    防御方
     * @param skill       技能配置
     * @param baseValue   技能基础值（已含属性系数）
     * @param random      战斗统一随机工具（保证固定种子可复现）
     * @param forceNoCrit 强制不暴击（供引擎内部结算控制）
     */
    public DamageResult calculate(BattleUnit attacker, BattleUnit defender, SkillConfig skill,
                                  double baseValue, GameRandom random, boolean forceNoCrit) {
        SystemRuleConfig rules = registry.getSystemRules();
        Map<String, com.petgame.config.model.StatusEffectConfig> statusIndex = registry.getStatusIndex();

        StatusModifiers attackerMod = StatusModifiers.of(attacker, statusIndex);
        StatusModifiers defenderMod = StatusModifiers.of(defender, statusIndex);

        DamageResult result = new DamageResult();
        result.rawBase = baseValue;

        // 1. 防御/抗性减伤（物理受防御、灵力/元素受抗性）
        double mitigated;
        if ("PHYSICAL".equalsIgnoreCase(skill.getDamageType())) {
            double effectiveDef = defender.getDefense() * defenderMod.getDefenseMultiplier();
            mitigated = mitigate(baseValue, effectiveDef, rules.getDefenseMitigationConstant());
        } else if ("MAGICAL".equalsIgnoreCase(skill.getDamageType())) {
            double effectiveRes = defender.getResistance() * defenderMod.getResistanceMultiplier();
            mitigated = mitigate(baseValue, effectiveRes, rules.getDefenseMitigationConstant());
        } else {
            mitigated = baseValue;
        }
        result.mitigated = mitigated;

        // 2. 属性克制
        double elementMultiplier = 1.0;
        String relation = "NEUTRAL";
        String skillElement = skill.getElement();
        if (skillElement != null && !"NONE".equalsIgnoreCase(skillElement)) {
            elementMultiplier = registry.getElementAdvantageMultiplier(skillElement, defender.getElement());
            if (elementMultiplier > rules.getNeutralMultiplier()) {
                relation = "ADVANTAGE";
            } else if (elementMultiplier < rules.getNeutralMultiplier()) {
                relation = "DISADVANTAGE";
            }
        }
        result.elementMultiplier = elementMultiplier;
        result.elementRelation = relation;

        // 3. 本属性加成
        double sameElementMultiplier = 1.0;
        if (skillElement != null && !"NONE".equalsIgnoreCase(skillElement)
                && skillElement.equalsIgnoreCase(attacker.getElement())) {
            sameElementMultiplier = rules.getSameElementBonus();
        }
        result.sameElementMultiplier = sameElementMultiplier;

        // 4. 状态联动（有限、配置化）
        double synergyMultiplier = 1.0;
        if (skillElement != null) {
            for (StatusesConfig.StatusSynergyConfig synergy : registry.getSynergies()) {
                if (skillElement.equalsIgnoreCase(synergy.getSkillElement())
                        && defender.hasStatus(synergy.getRequiredStatus())) {
                    synergyMultiplier *= synergy.getDamageMultiplier();
                }
            }
        }
        result.synergyMultiplier = synergyMultiplier;

        // 5. Buff / Debuff（含防御行动减伤）
        double buffMultiplier = attackerMod.getDamageDealtMultiplier() * defenderMod.getDamageTakenMultiplier();
        if (defender.isDefending()) {
            buffMultiplier *= rules.getDefendDamageReduction();
        }
        result.buffMultiplier = buffMultiplier;

        // 6. 暴击
        double critMultiplier = 1.0;
        boolean critical = false;
        if (!forceNoCrit && CriticalCalculator.roll(random, rules.getCritRate())) {
            critical = true;
            critMultiplier = CriticalCalculator.rollMultiplier(
                    random, rules.getCritMultiplierMin(), rules.getCritMultiplierMax());
        }
        result.critical = critical;
        result.critMultiplier = critMultiplier;

        // 7. 最终伤害（正常命中最低 1 点）
        double finalDamage = mitigated * elementMultiplier * sameElementMultiplier
                * synergyMultiplier * buffMultiplier * critMultiplier;
        result.finalDamage = Math.max(rules.getMinDamage(), (int) Math.round(finalDamage));
        return result;
    }

    /**
     * 伤害结算结果（含各段倍率，供战斗调试信息输出）。
     */
    @Getter
    public static class DamageResult {
        private double rawBase;
        private double mitigated;
        private double elementMultiplier = 1.0;
        private String elementRelation = "NEUTRAL";
        private double sameElementMultiplier = 1.0;
        private double synergyMultiplier = 1.0;
        private double buffMultiplier = 1.0;
        private boolean critical;
        private double critMultiplier = 1.0;
        private int finalDamage;
    }
}
