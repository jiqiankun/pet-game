package com.petgame.storage;

import com.petgame.common.GameRandom;
import com.petgame.config.model.ReleaseGiftsConfig;
import com.petgame.config.model.SystemRuleConfig;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 放生临别礼物计算器（阶段 5，规划文档 §9.3 决策七）。
 * <p>
 * 「礼物价值点数」底线规则：
 * <ul>
 *   <li>应得点数 = 稀有度基础值（20/60/150/400）× 捕获等级系数 × 培养系数；</li>
 *   <li>捕获等级系数 = 1 + 捕获等级 × releaseLevelFactorPerLevel，上限 releaseLevelFactorCap（×1.5）；</li>
 *   <li>培养系数 = 1 + min(已分配自由点数 / releaseCultivationPointsCap, 1) × (上限 - 1)，
 *       范围 1.0～releaseCultivationFactorMax（1.5），对应培养加成 ≤50% 的反套利约束；</li>
 *   <li>礼物从池中按权重随机抽取，直到累计单位价值 ≥ 四舍五入后的应得点数。</li>
 * </ul>
 * 纯函数实现，不依赖数据库，便于单元测试验证价值比例规则。
 */
public final class ReleaseGiftCalculator {

    /** 礼物抽取安全上限（防止配置异常导致死循环）。 */
    private static final int MAX_ROLLS = 1000;

    private ReleaseGiftCalculator() {
    }

    /**
     * 计算放生应得礼物价值点数（四舍五入取整）。
     *
     * @param rarity              种族稀有度（COMMON/RARE/EPIC/LEGENDARY）
     * @param capturedLevel       捕获等级（初始宠物 = 1）
     * @param allocatedFreePoints 已分配自由点数（培养程度度量）
     * @param rules               系统规则配置
     * @return 应得价值点数（≥ 稀有度基础值）
     */
    public static int computeGiftPoints(String rarity, int capturedLevel,
                                        int allocatedFreePoints, SystemRuleConfig rules) {
        int base = rules.getReleaseGiftBaseValue().getOrDefault(rarity, 0);
        double levelFactor = Math.min(
                1.0 + Math.max(0, capturedLevel) * rules.getReleaseLevelFactorPerLevel(),
                rules.getReleaseLevelFactorCap());
        double cultivationFactor = computeCultivationFactor(allocatedFreePoints, rules);
        return (int) Math.round(base * levelFactor * cultivationFactor);
    }

    /**
     * 计算培养系数：1.0（未培养）～ releaseCultivationFactorMax（培养满值）。
     * 培养程度以已分配自由点数度量，达到 releaseCultivationPointsCap 即满值。
     */
    public static double computeCultivationFactor(int allocatedFreePoints, SystemRuleConfig rules) {
        int cap = Math.max(1, rules.getReleaseCultivationPointsCap());
        double ratio = Math.min(1.0, Math.max(0, allocatedFreePoints) / (double) cap);
        return 1.0 + ratio * (rules.getReleaseCultivationFactorMax() - 1.0);
    }

    /**
     * 从礼物池按权重抽取礼物，直到累计单位价值 ≥ 应得点数（底线规则）。
     * <p>
     * 同类型同道具的抽取结果合并计数；返回结果的总价值必然 ≥ targetPoints。
     *
     * @param targetPoints 应得价值点数
     * @param pool         礼物池（unitValue 均 > 0，由配置校验保证）
     * @param random       统一随机源
     * @return 礼物结果列表（总价值 ≥ targetPoints）
     */
    public static List<GiftResult> rollGifts(int targetPoints,
                                             List<ReleaseGiftsConfig.GiftEntry> pool,
                                             GameRandom random) {
        if (pool == null || pool.isEmpty()) {
            throw new IllegalStateException("放生礼物池为空");
        }
        int totalWeight = pool.stream().mapToInt(ReleaseGiftsConfig.GiftEntry::getWeight).sum();
        if (totalWeight <= 0) {
            throw new IllegalStateException("放生礼物池权重总和必须大于 0");
        }

        // key = type + "#" + itemId（GOLD/EXP 无 itemId）
        Map<String, GiftResult> merged = new LinkedHashMap<>();
        int accumulated = 0;
        int rolls = 0;
        while (accumulated < targetPoints && rolls < MAX_ROLLS) {
            ReleaseGiftsConfig.GiftEntry entry = weightedRoll(pool, totalWeight, random);
            String key = entry.getType() + "#" + (entry.getItemId() == null ? "" : entry.getItemId());
            GiftResult result = merged.computeIfAbsent(key, k -> {
                GiftResult r = new GiftResult();
                r.setType(entry.getType());
                r.setItemId(entry.getItemId());
                return r;
            });
            result.setQuantity(result.getQuantity() + entry.getQuantity());
            result.setValue(result.getValue() + entry.getUnitValue());
            accumulated += entry.getUnitValue();
            rolls++;
        }
        return new ArrayList<>(merged.values());
    }

    /** 按权重抽取单个礼物条目。 */
    private static ReleaseGiftsConfig.GiftEntry weightedRoll(
            List<ReleaseGiftsConfig.GiftEntry> pool, int totalWeight, GameRandom random) {
        int roll = random.nextInt(1, totalWeight);
        int cumulative = 0;
        for (ReleaseGiftsConfig.GiftEntry entry : pool) {
            cumulative += entry.getWeight();
            if (roll <= cumulative) {
                return entry;
            }
        }
        return pool.get(pool.size() - 1);
    }

    /**
     * 单个礼物结果。
     */
    @lombok.Data
    public static class GiftResult {
        /** 类型：GOLD 金币 / EXP 经验 / ITEM 道具。 */
        private String type;
        /** ITEM 类型时的道具 ID。 */
        private String itemId;
        /** 发放数量。 */
        private int quantity;
        /** 该结果累计价值点数。 */
        private int value;
    }
}
