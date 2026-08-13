package com.petgame.inventory.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.petgame.common.BusinessException;
import com.petgame.config.GameConfigRegistry;
import com.petgame.config.model.ItemConfig;
import com.petgame.inventory.entity.PlayerInventoryEntity;
import com.petgame.inventory.mapper.PlayerInventoryMapper;
import com.petgame.pet.domain.PetGrowthService;
import com.petgame.pet.domain.PetPanelStats;
import com.petgame.pet.entity.PlayerPetEntity;
import com.petgame.pet.mapper.PlayerPetMapper;
import com.petgame.player.entity.PlayerEntity;
import com.petgame.player.mapper.PlayerMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 背包服务（阶段 4）。
 * <p>
 * 背包不限容量、按分类组织（捕捉/恢复/材料/技能书/重要物品），不做格子管理（需求 §93）。
 * 玩家存档只保存道具 ID 与数量，配置内容（名称、描述、效果）运行时从 items.yml 关联。
 * <p>
  * 阶段 4 实现恢复道具（HEAL_HP / REVIVE）的战斗外使用（需求 §92）。
  * 战斗内不使用恢复道具、不提供道具行动（用户裁决，见规划文档 §9.3 决策八）。
 */
@Service
public class InventoryService {

    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);

    private final PlayerMapper playerMapper;
    private final PlayerInventoryMapper playerInventoryMapper;
    private final PlayerPetMapper playerPetMapper;
    private final GameConfigRegistry registry;
    private final PetGrowthService growthService;

    public InventoryService(PlayerMapper playerMapper,
                            PlayerInventoryMapper playerInventoryMapper,
                            PlayerPetMapper playerPetMapper,
                            GameConfigRegistry registry,
                            PetGrowthService growthService) {
        this.playerMapper = playerMapper;
        this.playerInventoryMapper = playerInventoryMapper;
        this.playerPetMapper = playerPetMapper;
        this.registry = registry;
        this.growthService = growthService;
    }

    /**
     * 查询玩家背包（按分类组织，含道具配置摘要）。
     */
    public InventoryView getInventory() {
        PlayerEntity player = requirePlayer();

        List<PlayerInventoryEntity> records = playerInventoryMapper.selectList(
                new LambdaQueryWrapper<PlayerInventoryEntity>()
                        .eq(PlayerInventoryEntity::getSaveId, player.getSaveId()));

        List<InventoryView.ItemView> items = new ArrayList<>();
        for (PlayerInventoryEntity rec : records) {
            ItemConfig config = registry.getItem(rec.getItemId());
            if (config == null) {
                continue;
            }
            InventoryView.ItemView view = new InventoryView.ItemView();
            view.setItemId(config.getId());
            view.setName(config.getName());
            view.setDescription(config.getDescription());
            view.setCategory(config.getCategory());
            view.setItemType(config.getItemType());
            view.setValue(config.getValue());
            view.setUsableOutsideBattle(config.isUsableOutsideBattle());
            view.setUsableInBattle(config.isUsableInBattle());
            view.setDiscardable(config.isDiscardable());
            view.setQuantity(rec.getQuantity());
            items.add(view);
        }
        // 按 category 分组稳定排序，便于前端展示
        items.sort(Comparator.comparing(InventoryView.ItemView::getCategory)
                .thenComparing(InventoryView.ItemView::getName));

        InventoryView view = new InventoryView();
        view.setItems(items);
        view.setGold(player.getGold());
        return view;
    }

    /**
     * 战斗外使用恢复道具（需求 §92）。
     * <p>
     * HEAL_HP：恢复指定宠物 HP，不能超过最大 HP；满血时拒绝。
     * REVIVE：复活倒下的宠物（currentHp=0），恢复 value% 最大 HP；非倒下状态拒绝。
     * 使用后道具数量 -1，数量为 0 时删除记录。
     *
     * @param itemId 道具 ID
     * @param petId  目标宠物 ID
     * @return 使用结果（含宠物新 HP）
     */
    @Transactional
    public UseItemResult useRecoveryItem(String itemId, Long petId) {
        if (itemId == null || itemId.isBlank()) {
            throw new BusinessException("INVALID_ITEM", "道具 ID 不能为空");
        }
        if (petId == null) {
            throw new BusinessException("INVALID_PET", "宠物 ID 不能为空");
        }

        PlayerEntity player = requirePlayer();
        PlayerPetEntity pet = playerPetMapper.selectById(petId);
        if (pet == null || !player.getSaveId().equals(pet.getSaveId())) {
            throw new BusinessException("PET_NOT_OWNED", "宠物不存在或不属于当前存档");
        }

        ItemConfig item = registry.getItem(itemId);
        if (item == null) {
            throw new BusinessException("ITEM_NOT_FOUND", "道具配置不存在: " + itemId);
        }
        if (!item.isUsableOutsideBattle()) {
            throw new BusinessException("ITEM_NOT_USABLE_OUTSIDE", "该道具不能在战斗外使用: " + itemId);
        }

        PlayerInventoryEntity inv = playerInventoryMapper.selectOne(
                new LambdaQueryWrapper<PlayerInventoryEntity>()
                        .eq(PlayerInventoryEntity::getSaveId, player.getSaveId())
                        .eq(PlayerInventoryEntity::getItemId, itemId));
        if (inv == null || inv.getQuantity() <= 0) {
            throw new BusinessException("ITEM_OUT_OF_STOCK", "道具数量不足: " + itemId);
        }

        // 计算宠物最大 HP（统一面板公式）
        var species = registry.getSpecies(pet.getSpeciesId());
        if (species == null) {
            throw new BusinessException("SPECIES_CONFIG_MISSING", "种族配置缺失: " + pet.getSpeciesId());
        }
        PetPanelStats stats = growthService.computePanelStats(pet, species);
        int maxHp = stats.getMaxHp();
        int beforeHp = pet.getCurrentHp() != null ? pet.getCurrentHp() : 0;

        int afterHp;
        switch (item.getItemType()) {
            case "HEAL_HP":
                if (beforeHp >= maxHp) {
                    throw new BusinessException("HP_FULL", "宠物 HP 已满，无需恢复");
                }
                if (beforeHp <= 0) {
                    throw new BusinessException("PET_DEAD", "宠物已倒下，请使用复苏药剂");
                }
                afterHp = Math.min(maxHp, beforeHp + (int) Math.round(item.getValue()));
                break;
            case "REVIVE":
                if (beforeHp > 0) {
                    throw new BusinessException("PET_NOT_DEAD", "宠物未倒下，无需复苏");
                }
                afterHp = (int) Math.round(maxHp * item.getValue() / 100.0);
                afterHp = Math.max(1, Math.min(maxHp, afterHp));
                break;
            default:
                throw new BusinessException("ITEM_NOT_USABLE_OUTSIDE",
                        "该类型道具暂不支持战斗外使用: " + item.getItemType());
        }

        // 应用 HP 变化
        pet.setCurrentHp(afterHp);
        playerPetMapper.updateById(pet);

        // 扣减道具
        inv.setQuantity(inv.getQuantity() - 1);
        if (inv.getQuantity() <= 0) {
            playerInventoryMapper.deleteById(inv.getId());
        } else {
            playerInventoryMapper.updateById(inv);
        }

        UseItemResult result = new UseItemResult();
        result.setItemId(itemId);
        result.setItemName(item.getName());
        result.setPetId(petId);
        result.setBeforeHp(beforeHp);
        result.setAfterHp(afterHp);
        result.setMaxHp(maxHp);
        result.setRemainingQuantity(inv.getQuantity());

        log.info("使用道具：player={}, item={}, pet={}, HP {}→{}, 剩余 {}",
                player.getPlayerName(), itemId, petId, beforeHp, afterHp, inv.getQuantity());
        return result;
    }

    // ==================== 内部工具 ====================

    private PlayerEntity requirePlayer() {
        PlayerEntity player = playerMapper.selectOne(null);
        if (player == null) {
            throw new BusinessException("NO_SAVE", "不存在存档，请先创建新游戏");
        }
        return player;
    }

    // ==================== DTO ====================

    /** 背包视图。 */
    @lombok.Data
    public static class InventoryView {
        private List<ItemView> items = new ArrayList<>();
        private Integer gold;

        @lombok.Data
        public static class ItemView {
            private String itemId;
            private String name;
            private String description;
            private String category;
            private String itemType;
            private double value;
            private boolean usableOutsideBattle;
            private boolean usableInBattle;
            private boolean discardable;
            private int quantity;
        }
    }

    /** 使用道具结果。 */
    @lombok.Data
    public static class UseItemResult {
        private String itemId;
        private String itemName;
        private Long petId;
        private int beforeHp;
        private int afterHp;
        private int maxHp;
        private int remainingQuantity;
    }
}
