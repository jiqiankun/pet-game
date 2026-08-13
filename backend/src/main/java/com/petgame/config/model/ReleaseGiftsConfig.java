package com.petgame.config.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 放生临别礼物池配置（drops/release-gifts.yml）。
 * <p>
 * 放生礼物采用「礼物价值点数」底线规则（规划文档 §9.3 决策七）：
 * 应得点数 = 稀有度基础值 × 捕获等级系数 × 培养系数；
 * 礼物从池中按权重随机抽取，直到累计单位价值 ≥ 应得点数。
 * 池内物品只包含有实际价值的资源（金币、经验、道具），不发放纯展示物。
 */
@Data
@NoArgsConstructor
public class ReleaseGiftsConfig {

    /** 配置结构版本。 */
    private int configVersion = 1;

    /** 礼物池。 */
    private List<GiftEntry> gifts = new ArrayList<>();

    /**
     * 单个礼物条目。
     */
    @Data
    @NoArgsConstructor
    public static class GiftEntry {
        /** 类型：GOLD 金币 / EXP 经验 / ITEM 道具。 */
        private String type;

        /** ITEM 类型时的道具 ID。 */
        private String itemId;

        /** 单次抽中发放的数量（金币数量 / 经验数量 / 道具数量）。 */
        private int quantity = 1;

        /** 单位价值点数（单次数量的总价值点数 = unitValue，用于底线校验）。 */
        private int unitValue;

        /** 抽取权重。 */
        private int weight = 1;
    }
}
