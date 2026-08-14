package com.petgame.config.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 商店配置模型（阶段 10）。
 * <p>
 * 对应 shop/shop.yml，定义商店商品列表。
 * 商品随主线推进固定解锁，不做每日刷新/限购/随机商店/倒计时（需求 §95）。
 */
@Data
@NoArgsConstructor
public class ShopConfig {

    /** 商品列表（按配置顺序展示）。 */
    private List<ShopItemConfig> shopItems = new ArrayList<>();

    /**
     * 单个商品配置。
     */
    @Data
    @NoArgsConstructor
    public static class ShopItemConfig {

        /** 道具 ID（引用 items.yml）。 */
        private String itemId;

        /** 售价（金币）。优先使用此字段，未设置时回退到 ItemConfig.price。 */
        private int price = 0;

        /** 解锁所需完成的任务 ID（null 或空表示始终可用）。 */
        private String unlockQuestId;

        /** 商品分类标签（可选，用于前端分组展示：CAPTURE/RECOVERY/SKILL_BOOK/MATERIAL）。 */
        private String category;
    }
}
