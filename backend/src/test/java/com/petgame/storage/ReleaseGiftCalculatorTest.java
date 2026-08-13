package com.petgame.storage;

import com.petgame.common.GameRandom;
import com.petgame.config.model.ReleaseGiftsConfig;
import com.petgame.config.model.SystemRuleConfig;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 放生礼物计算测试（阶段 5，测试规约 §2.5 / 规划文档 §9.3 决策七）。
 * <p>
 * 价值比例规则：四档基础值 20/60/150/400；
 * 捕获等级系数 = 1 + 等级 × 1%（上限 ×1.5）；
 * 培养系数 1.0～1.5（培养加成不超过 50%）；
 * 礼物抽取总价值 ≥ 四舍五入后的应得点数（底线规则）。
 */
class ReleaseGiftCalculatorTest {

    /** 构造与 system.yml 一致的放生规则。 */
    private SystemRuleConfig rules() {
        SystemRuleConfig rules = new SystemRuleConfig();
        rules.getReleaseGiftBaseValue().put("COMMON", 20);
        rules.getReleaseGiftBaseValue().put("RARE", 60);
        rules.getReleaseGiftBaseValue().put("EPIC", 150);
        rules.getReleaseGiftBaseValue().put("LEGENDARY", 400);
        rules.setReleaseLevelFactorPerLevel(0.01);
        rules.setReleaseLevelFactorCap(1.5);
        rules.setReleaseCultivationFactorMax(1.5);
        rules.setReleaseCultivationPointsCap(100);
        return rules;
    }

    private List<ReleaseGiftsConfig.GiftEntry> giftPool() {
        return List.of(
                gift("GOLD", null, 5, 1, 50),
                gift("EXP", null, 5, 1, 30),
                gift("ITEM", "ITEM_POTION_SMALL", 1, 10, 20));
    }

    private ReleaseGiftsConfig.GiftEntry gift(String type, String itemId,
                                              int quantity, int unitValue, int weight) {
        ReleaseGiftsConfig.GiftEntry entry = new ReleaseGiftsConfig.GiftEntry();
        entry.setType(type);
        entry.setItemId(itemId);
        entry.setQuantity(quantity);
        entry.setUnitValue(unitValue);
        entry.setWeight(weight);
        return entry;
    }

    @Test
    void baseValue_byRarity() {
        SystemRuleConfig rules = rules();
        // 捕获等级 0、未培养 → 点数 = 基础值 × 1.0 × 1.0
        assertEquals(20, ReleaseGiftCalculator.computeGiftPoints("COMMON", 0, 0, rules));
        assertEquals(60, ReleaseGiftCalculator.computeGiftPoints("RARE", 0, 0, rules));
        assertEquals(150, ReleaseGiftCalculator.computeGiftPoints("EPIC", 0, 0, rules));
        assertEquals(400, ReleaseGiftCalculator.computeGiftPoints("LEGENDARY", 0, 0, rules));
    }

    @Test
    void levelFactor_onePercentPerLevel_withCap() {
        SystemRuleConfig rules = rules();
        // COMMON 基础 20：等级 10 → 20 × 1.10 = 22
        assertEquals(22, ReleaseGiftCalculator.computeGiftPoints("COMMON", 10, 0, rules));
        // 等级 50 → 20 × 1.5 = 30（达到上限）
        assertEquals(30, ReleaseGiftCalculator.computeGiftPoints("COMMON", 50, 0, rules));
        // 等级 100 → 仍为上限 ×1.5
        assertEquals(30, ReleaseGiftCalculator.computeGiftPoints("COMMON", 100, 0, rules));
    }

    @Test
    void cultivationFactor_neverExceedsFiftyPercent() {
        SystemRuleConfig rules = rules();
        int untrained = ReleaseGiftCalculator.computeGiftPoints("COMMON", 0, 0, rules);
        assertEquals(1.0, ReleaseGiftCalculator.computeCultivationFactor(0, rules), 1e-9);
        // 培养半满（50/100 点）→ 系数 1.25
        assertEquals(1.25, ReleaseGiftCalculator.computeCultivationFactor(50, rules), 1e-9);
        // 培养满值（100 点）→ 系数 1.5
        assertEquals(1.5, ReleaseGiftCalculator.computeCultivationFactor(100, rules), 1e-9);
        // 超过满值仍封顶 1.5（培养加成不超过 50%）
        assertEquals(1.5, ReleaseGiftCalculator.computeCultivationFactor(99999, rules), 1e-9);
        // 点数体现在礼物上：满培养 = 未培养 × 1.5
        int fullTrained = ReleaseGiftCalculator.computeGiftPoints("COMMON", 0, 100, rules);
        assertEquals((int) Math.round(untrained * 1.5), fullTrained);
    }

    @Test
    void rollGifts_totalValue_shouldMeetFloorRule() {
        List<ReleaseGiftsConfig.GiftEntry> pool = giftPool();
        // 多个点数档位 × 固定种子：总价值必须 ≥ 应得点数（底线规则）
        for (int targetPoints : new int[]{1, 15, 20, 63, 150, 420, 900}) {
            for (long seed = 1; seed <= 5; seed++) {
                List<ReleaseGiftCalculator.GiftResult> gifts =
                        ReleaseGiftCalculator.rollGifts(targetPoints, pool, new GameRandom(seed));
                assertFalse(gifts.isEmpty(), "礼物不应为空");
                int totalValue = gifts.stream().mapToInt(ReleaseGiftCalculator.GiftResult::getValue).sum();
                assertTrue(totalValue >= targetPoints,
                        "礼物总价值(" + totalValue + ") 必须 ≥ 应得点数(" + targetPoints + ")");
                gifts.forEach(g -> assertTrue(g.getQuantity() > 0, "礼物数量必须为正"));
            }
        }
    }

    @Test
    void rollGifts_fixedSeed_reproducible() {
        List<ReleaseGiftsConfig.GiftEntry> pool = giftPool();
        List<ReleaseGiftCalculator.GiftResult> first =
                ReleaseGiftCalculator.rollGifts(100, pool, new GameRandom(42));
        List<ReleaseGiftCalculator.GiftResult> second =
                ReleaseGiftCalculator.rollGifts(100, pool, new GameRandom(42));
        assertEquals(first.size(), second.size());
        for (int i = 0; i < first.size(); i++) {
            assertEquals(first.get(i).getType(), second.get(i).getType());
            assertEquals(first.get(i).getItemId(), second.get(i).getItemId());
            assertEquals(first.get(i).getQuantity(), second.get(i).getQuantity());
        }
    }

    @Test
    void rollGifts_emptyPool_shouldThrow() {
        assertThrows(IllegalStateException.class, () ->
                ReleaseGiftCalculator.rollGifts(10, List.of(), new GameRandom(1)));
    }
}
