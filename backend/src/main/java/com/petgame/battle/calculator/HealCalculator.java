package com.petgame.battle.calculator;

import com.petgame.battle.model.BattleUnit;
import com.petgame.config.GameConfigRegistry;
import com.petgame.config.model.SkillConfig;

/**
 * 治疗与护盾计算器。
 * <p>
 * 治疗与护盾效果 = 基础值 + 属性系数，可享受本属性加成（效果 ×1.20）。
 * 治疗第一阶段不能暴击（不调用 CriticalCalculator）。
 * 护盾被攻击时不再次套用防御计算（需求文档 §42）。
 */
public class HealCalculator {

    private final GameConfigRegistry registry;

    public HealCalculator(GameConfigRegistry registry) {
        this.registry = registry;
    }

    /**
     * 计算治疗量（不暴击）。
     */
    public int calculateHeal(BattleUnit caster, SkillConfig skill) {
        double base = DamageCalculator.computeBaseValue(skill, caster);
        return (int) Math.round(base * sameElementBonus(caster, skill));
    }

    /**
     * 计算护盾值（不暴击）。
     */
    public int calculateShield(BattleUnit caster, SkillConfig skill) {
        double base = DamageCalculator.computeBaseValue(skill, caster);
        return (int) Math.round(base * sameElementBonus(caster, skill));
    }

    /** 本属性加成：技能属性与释放者属性相同且非 NONE。 */
    private double sameElementBonus(BattleUnit caster, SkillConfig skill) {
        String skillElement = skill.getElement();
        if (skillElement != null && !"NONE".equalsIgnoreCase(skillElement)
                && skillElement.equalsIgnoreCase(caster.getElement())) {
            return registry.getSystemRules().getSameElementBonus();
        }
        return 1.0;
    }
}
