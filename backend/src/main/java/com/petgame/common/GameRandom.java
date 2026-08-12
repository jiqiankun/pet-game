package com.petgame.common;

import java.util.Random;

/**
 * 统一随机工具。
 * <p>
 * 所有随机场景（暴击、命中、捕获、掉落、资质、稀有技能、特殊外观、放生礼物）
 * 统一使用此类，禁止业务代码直接使用 {@link Math#random()} 或 {@code new Random()}。
 * <p>
 * 支持固定种子，可复现完整随机流程（用于 Bug 复现和战斗平衡测试）。
 */
public class GameRandom {

    private final Random random;
    private final long seed;

    /**
     * 以随机种子创建。
     */
    public GameRandom() {
        this.seed = System.nanoTime();
        this.random = new Random(this.seed);
    }

    /**
     * 以固定种子创建（可复现）。
     *
     * @param seed 随机种子
     */
    public GameRandom(long seed) {
        this.seed = seed;
        this.random = new Random(seed);
    }

    /** 获取当前种子。 */
    public long getSeed() {
        return seed;
    }

    /**
     * 返回 [min, max] 范围内的均匀随机整数。
     */
    public int nextInt(int min, int max) {
        if (min > max) {
            throw new IllegalArgumentException("min(" + min + ") > max(" + max + ")");
        }
        if (min == max) {
            return min;
        }
        return min + random.nextInt(max - min + 1);
    }

    /**
     * 返回 [min, max] 范围内的均匀随机 double。
     */
    public double nextDouble(double min, double max) {
        if (min > max) {
            throw new IllegalArgumentException("min(" + min + ") > max(" + max + ")");
        }
        if (min == max) {
            return min;
        }
        return min + random.nextDouble() * (max - min);
    }

    /**
     * 按概率判定是否命中。
     *
     * @param probability 命中概率 [0.0, 1.0]
     * @return true 表示命中
     */
    public boolean chance(double probability) {
        if (probability <= 0.0) return false;
        if (probability >= 1.0) return true;
        return random.nextDouble() < probability;
    }

    /**
     * 返回下一个随机 long 值。
     */
    public long nextLong() {
        return random.nextLong();
    }
}
