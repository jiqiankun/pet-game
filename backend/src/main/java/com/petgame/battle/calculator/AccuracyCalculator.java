package com.petgame.battle.calculator;

import com.petgame.common.GameRandom;
import com.petgame.config.model.SkillConfig;

/**
 * 命中计算器。
 * <p>
 * 技能命中率配置化；致盲等状态通过攻击方命中惩罚影响命中。
 */
public final class AccuracyCalculator {

    private AccuracyCalculator() {
    }

    /**
     * 有效命中率 = 技能命中率 - 攻击方命中惩罚，收敛到 [0, 1]。
     */
    public static double effectiveAccuracy(SkillConfig skill, StatusModifiers attackerMod) {
        double accuracy = skill.getAccuracy() - attackerMod.getAccuracyPenalty();
        return Math.max(0.0, Math.min(1.0, accuracy));
    }

    /**
     * 命中判定。
     */
    public static boolean roll(GameRandom random, SkillConfig skill, StatusModifiers attackerMod) {
        return random.chance(effectiveAccuracy(skill, attackerMod));
    }
}
