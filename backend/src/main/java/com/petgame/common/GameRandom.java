package com.petgame.common;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 统一随机工具。
 * <p>
 * 所有随机场景（暴击、命中、捕获、掉落、资质、稀有技能、特殊外观、放生礼物）
 * 统一使用此类，禁止业务代码直接使用 {@link Math#random()} 或 {@code new Random()}。
 * <p>
 * 支持固定种子，可复现完整随机流程（用于 Bug 复现和战斗平衡测试）。
 * <p>
 * 支持可选随机序列录制（随机数调试）：开启后记录每次随机取值的调用与结果，
 * 用于开发者工具「随机数调试」查看本次随机值序列。
 */
public class GameRandom {

    private final Random random;
    private final long seed;

    /** 是否录制随机序列（随机数调试，默认关闭）。 */
    private boolean recordDraws;

    /** 已录制的随机调用序列（仅 recordDraws 开启时记录）。 */
    private final List<String> drawLog = new ArrayList<>();

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

    /** 开启/关闭随机序列录制（随机数调试）。 */
    public void setRecordDraws(boolean record) {
        this.recordDraws = record;
    }

    /** 是否正在录制随机序列。 */
    public boolean isRecordDraws() {
        return recordDraws;
    }

    /** 清空已录制的随机序列。 */
    public void clearDrawLog() {
        drawLog.clear();
    }

    /** 返回已录制的随机调用序列（随机数调试用，可能较多，调用方自行截取）。 */
    public List<String> getDrawLog() {
        return drawLog;
    }

    /** 记录一次随机调用（仅录制开启时有效）。 */
    private void record(String method, String detail, Object result) {
        if (recordDraws) {
            drawLog.add(method + "(" + detail + ") -> " + result);
        }
    }

    /**
     * 返回 [min, max] 范围内的均匀随机整数。
     */
    public int nextInt(int min, int max) {
        if (min > max) {
            throw new IllegalArgumentException("min(" + min + ") > max(" + max + ")");
        }
        if (min == max) {
            if (recordDraws) {
                drawLog.add("nextInt(" + min + "," + max + ") -> " + min);
            }
            return min;
        }
        int v = min + random.nextInt(max - min + 1);
        record("nextInt", min + "," + max, v);
        return v;
    }

    /**
     * 返回 [min, max] 范围内的均匀随机 double。
     */
    public double nextDouble(double min, double max) {
        if (min > max) {
            throw new IllegalArgumentException("min(" + min + ") > max(" + max + ")");
        }
        if (min == max) {
            if (recordDraws) {
                drawLog.add("nextDouble(" + min + "," + max + ") -> " + min);
            }
            return min;
        }
        double v = min + random.nextDouble() * (max - min);
        record("nextDouble", min + "," + max, v);
        return v;
    }

    /**
     * 按概率判定是否命中。
     *
     * @param probability 命中概率 [0.0, 1.0]
     * @return true 表示命中
     */
    public boolean chance(double probability) {
        if (probability <= 0.0) {
            if (recordDraws) {
                drawLog.add("chance(" + probability + ") -> false");
            }
            return false;
        }
        if (probability >= 1.0) {
            if (recordDraws) {
                drawLog.add("chance(" + probability + ") -> true");
            }
            return true;
        }
        boolean r = random.nextDouble() < probability;
        record("chance", String.valueOf(probability), r);
        return r;
    }

    /**
     * 返回下一个随机 long 值。
     */
    public long nextLong() {
        long v = random.nextLong();
        record("nextLong", "", v);
        return v;
    }
}
