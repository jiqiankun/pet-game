package com.petgame.battle.calculator;

import com.petgame.common.GameRandom;

/**
 * 暴击计算器。
 * <p>
 * 基础暴击率 5%，暴击倍率 1.4～2.0 均匀随机（均配置化）。
 * 治疗第一阶段不能暴击（由引擎保证不调用本类）。
 */
public final class CriticalCalculator {

    private CriticalCalculator() {
    }

    /**
     * 暴击判定。
     *
     * @param random   统一随机工具
     * @param critRate 暴击率 [0, 1]
     */
    public static boolean roll(GameRandom random, double critRate) {
        return random.chance(critRate);
    }

    /**
     * 暴击倍率（[min, max] 均匀随机）。
     */
    public static double rollMultiplier(GameRandom random, double min, double max) {
        return random.nextDouble(min, max);
    }
}
