package com.petgame.shop.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.petgame.common.BusinessException;
import com.petgame.config.GameConfigRegistry;
import com.petgame.config.model.ItemConfig;
import com.petgame.config.model.QuestsConfig;
import com.petgame.config.model.ShopConfig;
import com.petgame.inventory.entity.PlayerInventoryEntity;
import com.petgame.inventory.mapper.PlayerInventoryMapper;
import com.petgame.player.entity.PlayerEntity;
import com.petgame.player.mapper.PlayerMapper;
import com.petgame.quest.entity.PlayerQuestEntity;
import com.petgame.quest.mapper.PlayerQuestMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 商店服务（阶段 10）。
 * <p>
 * 商品随主线推进固定解锁（需求 §95）。
 * 不做每日刷新、限购、随机商店、倒计时商品。
 * 普通捕捉球长期常驻。
 */
@Service
public class ShopService {

    private static final Logger log = LoggerFactory.getLogger(ShopService.class);

    private final PlayerMapper playerMapper;
    private final PlayerInventoryMapper inventoryMapper;
    private final PlayerQuestMapper questMapper;
    private final GameConfigRegistry registry;

    public ShopService(PlayerMapper playerMapper,
                       PlayerInventoryMapper inventoryMapper,
                       PlayerQuestMapper questMapper,
                       GameConfigRegistry registry) {
        this.playerMapper = playerMapper;
        this.inventoryMapper = inventoryMapper;
        this.questMapper = questMapper;
        this.registry = registry;
    }

    /**
     * 查询商店商品列表（含解锁状态与玩家金币）。
     */
    public ShopView getShopView() {
        PlayerEntity player = requirePlayer();
        ShopConfig shopConfig = registry.getShopConfig();
        if (shopConfig == null || shopConfig.getShopItems() == null) {
            ShopView view = new ShopView();
            view.setGold(player.getGold());
            view.setItems(new ArrayList<>());
            return view;
        }

        List<ShopView.ShopItemView> items = new ArrayList<>();
        for (ShopConfig.ShopItemConfig shopItem : shopConfig.getShopItems()) {
            ItemConfig itemConfig = registry.getItem(shopItem.getItemId());
            if (itemConfig == null) {
                continue;
            }

            boolean unlocked = isUnlocked(player, shopItem.getUnlockQuestId());

            ShopView.ShopItemView itemView = new ShopView.ShopItemView();
            itemView.setItemId(itemConfig.getId());
            itemView.setName(itemConfig.getName());
            itemView.setDescription(itemConfig.getDescription());
            itemView.setCategory(shopItem.getCategory() != null ? shopItem.getCategory() : itemConfig.getCategory());
            itemView.setItemType(itemConfig.getItemType());
            itemView.setPrice(shopItem.getPrice() > 0 ? shopItem.getPrice() : itemConfig.getPrice());
            itemView.setUnlocked(unlocked);
            itemView.setUnlockQuestId(shopItem.getUnlockQuestId());
            items.add(itemView);
        }

        ShopView view = new ShopView();
        view.setGold(player.getGold());
        view.setItems(items);
        return view;
    }

    /**
     * 购买商品（单事务：扣金币 + 加背包数量）。
     *
     * @param itemId   道具 ID
     * @param quantity 购买数量（必须 ≥ 1）
     */
    @Transactional
    public BuyResult buyItem(String itemId, int quantity) {
        if (itemId == null || itemId.isBlank()) {
            throw new BusinessException("INVALID_ITEM", "道具 ID 不能为空");
        }
        if (quantity <= 0) {
            throw new BusinessException("INVALID_QUANTITY", "购买数量必须为正数");
        }

        PlayerEntity player = requirePlayer();
        ShopConfig shopConfig = registry.getShopConfig();

        // 查找商店商品配置
        ShopConfig.ShopItemConfig shopItem = null;
        if (shopConfig != null && shopConfig.getShopItems() != null) {
            for (ShopConfig.ShopItemConfig si : shopConfig.getShopItems()) {
                if (itemId.equals(si.getItemId())) {
                    shopItem = si;
                    break;
                }
            }
        }
        if (shopItem == null) {
            throw new BusinessException("ITEM_NOT_IN_SHOP", "该道具不在商店中: " + itemId);
        }

        // 校验解锁状态
        if (!isUnlocked(player, shopItem.getUnlockQuestId())) {
            throw new BusinessException("SHOP_ITEM_LOCKED", "该商品尚未解锁，需完成对应主线任务");
        }

        ItemConfig itemConfig = registry.getItem(itemId);
        if (itemConfig == null) {
            throw new BusinessException("ITEM_NOT_FOUND", "道具配置不存在: " + itemId);
        }

        int unitPrice = shopItem.getPrice() > 0 ? shopItem.getPrice() : itemConfig.getPrice();
        int totalCost = unitPrice * quantity;

        // 校验金币
        if (player.getGold() < totalCost) {
            throw new BusinessException("GOLD_NOT_ENOUGH",
                    "金币不足：需要 " + totalCost + "，当前 " + player.getGold());
        }

        // 扣金币
        player.setGold(player.getGold() - totalCost);
        playerMapper.updateById(player);

        // 加背包
        PlayerInventoryEntity inv = inventoryMapper.selectOne(
                new LambdaQueryWrapper<PlayerInventoryEntity>()
                        .eq(PlayerInventoryEntity::getSaveId, player.getSaveId())
                        .eq(PlayerInventoryEntity::getItemId, itemId));
        if (inv == null) {
            inv = new PlayerInventoryEntity();
            inv.setSaveId(player.getSaveId());
            inv.setItemId(itemId);
            inv.setQuantity(quantity);
            inventoryMapper.insert(inv);
        } else {
            inv.setQuantity(inv.getQuantity() + quantity);
            inventoryMapper.updateById(inv);
        }

        BuyResult result = new BuyResult();
        result.setItemId(itemId);
        result.setItemName(itemConfig.getName());
        result.setQuantity(quantity);
        result.setUnitPrice(unitPrice);
        result.setTotalCost(totalCost);
        result.setRemainingGold(player.getGold());

        log.info("商店购买：player={}, item={} ×{}, 花费 {}金币, 剩余 {}金币",
                player.getPlayerName(), itemId, quantity, totalCost, player.getGold());
        return result;
    }

    // ==================== 内部工具 ====================

    private boolean isUnlocked(PlayerEntity player, String unlockQuestId) {
        if (unlockQuestId == null || unlockQuestId.isBlank()) {
            return true;
        }
        PlayerQuestEntity quest = questMapper.selectOne(
                new LambdaQueryWrapper<PlayerQuestEntity>()
                        .eq(PlayerQuestEntity::getSaveId, player.getSaveId())
                        .eq(PlayerQuestEntity::getQuestId, unlockQuestId));
        return quest != null && "COMPLETED".equals(quest.getStatus());
    }

    private PlayerEntity requirePlayer() {
        PlayerEntity player = playerMapper.selectOne(null);
        if (player == null) {
            throw new BusinessException("NO_SAVE", "不存在存档，请先创建新游戏");
        }
        return player;
    }

    // ==================== DTO ====================

    @lombok.Data
    public static class ShopView {
        private Integer gold;
        private List<ShopItemView> items = new ArrayList<>();

        @lombok.Data
        public static class ShopItemView {
            private String itemId;
            private String name;
            private String description;
            private String category;
            private String itemType;
            private int price;
            private boolean unlocked;
            private String unlockQuestId;
        }
    }

    @lombok.Data
    public static class BuyResult {
        private String itemId;
        private String itemName;
        private int quantity;
        private int unitPrice;
        private int totalCost;
        private int remainingGold;
    }
}
