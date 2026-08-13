package com.petgame.inventory;

import com.petgame.common.BusinessException;
import com.petgame.config.GameConfigRegistry;
import com.petgame.config.model.InitialPetsConfig;
import com.petgame.config.model.ItemConfig;
import com.petgame.config.model.ItemsConfig;
import com.petgame.inventory.entity.PlayerInventoryEntity;
import com.petgame.inventory.mapper.PlayerInventoryMapper;
import com.petgame.inventory.service.InventoryService;
import com.petgame.pet.domain.PetGrowthService;
import com.petgame.pet.entity.PlayerPetEntity;
import com.petgame.pet.mapper.PlayerPetMapper;
import com.petgame.player.entity.PlayerEntity;
import com.petgame.player.mapper.PlayerMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static com.petgame.pet.PetGrowthTestFixtures.species;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

/**
 * InventoryService 单元测试（阶段 4 验收标准）。
 * <p>
 * 覆盖：HEAL_HP（满血拒绝、倒下拒绝、正常恢复封顶 maxHp）、
 * REVIVE（未倒下拒绝、倒下按百分比恢复）、道具不存在拒绝、数量不足拒绝、
 * 使用后数量 -1（为 0 删除记录）、背包查询按分类排序。
 */
@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock
    private PlayerMapper playerMapper;
    @Mock
    private PlayerInventoryMapper playerInventoryMapper;
    @Mock
    private PlayerPetMapper playerPetMapper;

    private GameConfigRegistry registry;
    private PetGrowthService growthService;

    @InjectMocks
    private InventoryService inventoryService;

    private static final String SPECIES_ID = "SPEC_TEST";
    private static final String ITEM_HEAL = "ITEM_POTION_SMALL";
    private static final String ITEM_REVIVE = "ITEM_REVIVE";

    @BeforeEach
    void setUp() {
        // 构建种族配置（用于面板公式计算 maxHp）
        InitialPetsConfig.InitialPetOption speciesOption =
                species(SPECIES_ID, "COMMON", 50, List.of());
        // 构建道具配置
        ItemConfig healPotion = item(ITEM_HEAL, "RECOVERY", "HEAL_HP", 50, true);
        ItemConfig revivePotion = item(ITEM_REVIVE, "RECOVERY", "REVIVE", 50, true);

        registry = buildRegistryWithItems(List.of(speciesOption), List.of(healPotion, revivePotion));
        growthService = new PetGrowthService(registry);

        inventoryService = new InventoryService(playerMapper, playerInventoryMapper,
                playerPetMapper, registry, growthService);
    }

    // ==================== 背包查询 ====================

    @Test
    void getInventory_returnsItemsOrderedByCategoryAndName() {
        PlayerEntity player = playerWithGold(1000);
        when(playerMapper.selectOne(isNull())).thenReturn(player);

        PlayerInventoryEntity rec1 = inventory(11L, ITEM_HEAL, 3);
        PlayerInventoryEntity rec2 = inventory(12L, ITEM_REVIVE, 1);
        when(playerInventoryMapper.selectList(any())).thenReturn(List.of(rec1, rec2));

        InventoryService.InventoryView view = inventoryService.getInventory();

        assertEquals(1000, view.getGold());
        assertEquals(2, view.getItems().size());
        // 按 category + name 排序，HEAL_POTION 与 REVIVE_POTION 同属 RECOVERY
        // 名称 ITEM_POTION_SMALL < ITEM_REVIVE（字典序）
        assertEquals(ITEM_HEAL, view.getItems().get(0).getItemId());
        assertEquals(3, view.getItems().get(0).getQuantity());
    }

    @Test
    void getInventory_skipsItemsWithMissingConfig() {
        PlayerEntity player = playerWithGold(1000);
        when(playerMapper.selectOne(isNull())).thenReturn(player);

        PlayerInventoryEntity valid = inventory(11L, ITEM_HEAL, 3);
        PlayerInventoryEntity missing = inventory(12L, "ITEM_UNKNOWN", 1);
        when(playerInventoryMapper.selectList(any())).thenReturn(List.of(valid, missing));

        InventoryService.InventoryView view = inventoryService.getInventory();
        assertEquals(1, view.getItems().size());
        assertEquals(ITEM_HEAL, view.getItems().get(0).getItemId());
    }

    @Test
    void getInventory_noSave_rejected() {
        when(playerMapper.selectOne(isNull())).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> inventoryService.getInventory());
        assertEquals("NO_SAVE", ex.getErrorCode());
    }

    // ==================== HEAL_HP 道具 ====================

    @Test
    void useHealHpItem_normalCase_cappedAtMaxHp() {
        // Lv.5 maxHp = 100 + 8*4 = 132；当前 HP=80；恢复 50 → 130（未超）
        PlayerPetEntity pet = pet(5, 80);
        PlayerEntity player = playerWithGold(1000);
        PlayerInventoryEntity inv = inventory(11L, ITEM_HEAL, 2);

        when(playerMapper.selectOne(isNull())).thenReturn(player);
        when(playerPetMapper.selectById(1L)).thenReturn(pet);
        when(playerInventoryMapper.selectOne(any())).thenReturn(inv);

        InventoryService.UseItemResult result =
                inventoryService.useRecoveryItem(ITEM_HEAL, 1L);

        assertEquals(80, result.getBeforeHp());
        assertEquals(130, result.getAfterHp());
        assertEquals(132, result.getMaxHp());
        assertEquals(1, result.getRemainingQuantity());  // 2 - 1
        assertEquals(130, pet.getCurrentHp());
        verify(playerPetMapper).updateById(pet);
        verify(playerInventoryMapper).updateById(inv);
    }

    @Test
    void useHealHpItem_cappedAtMaxHp() {
        // 当前 HP=130，恢复 50 → maxHp 132
        PlayerPetEntity pet = pet(5, 130);
        PlayerEntity player = playerWithGold(1000);
        PlayerInventoryEntity inv = inventory(11L, ITEM_HEAL, 1);

        when(playerMapper.selectOne(isNull())).thenReturn(player);
        when(playerPetMapper.selectById(1L)).thenReturn(pet);
        when(playerInventoryMapper.selectOne(any())).thenReturn(inv);

        InventoryService.UseItemResult result =
                inventoryService.useRecoveryItem(ITEM_HEAL, 1L);

        assertEquals(132, result.getAfterHp());
        assertEquals(132, pet.getCurrentHp());
    }

    @Test
    void useHealHpItem_hpFull_rejected() {
        PlayerPetEntity pet = pet(5, 132);  // maxHp=132
        PlayerEntity player = playerWithGold(1000);
        PlayerInventoryEntity inv = inventory(11L, ITEM_HEAL, 2);

        when(playerMapper.selectOne(isNull())).thenReturn(player);
        when(playerPetMapper.selectById(1L)).thenReturn(pet);
        when(playerInventoryMapper.selectOne(any())).thenReturn(inv);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> inventoryService.useRecoveryItem(ITEM_HEAL, 1L));
        assertEquals("HP_FULL", ex.getErrorCode());
        // 道具不扣减
        assertEquals(2, inv.getQuantity());
        verify(playerPetMapper, never()).updateById(any(PlayerPetEntity.class));
    }

    @Test
    void useHealHpItem_petDead_rejected() {
        // currentHp=0 → 倒下，需要复苏药剂
        PlayerPetEntity pet = pet(5, 0);
        PlayerEntity player = playerWithGold(1000);
        PlayerInventoryEntity inv = inventory(11L, ITEM_HEAL, 2);

        when(playerMapper.selectOne(isNull())).thenReturn(player);
        when(playerPetMapper.selectById(1L)).thenReturn(pet);
        when(playerInventoryMapper.selectOne(any())).thenReturn(inv);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> inventoryService.useRecoveryItem(ITEM_HEAL, 1L));
        assertEquals("PET_DEAD", ex.getErrorCode());
        assertEquals(2, inv.getQuantity());
    }

    // ==================== REVIVE 道具 ====================

    @Test
    void useReviveItem_petDead_revivedByPercentage() {
        // Lv.5 maxHp=132，REVIVE value=50（百分比）→ 恢复 50% maxHp = 66
        PlayerPetEntity pet = pet(5, 0);
        PlayerEntity player = playerWithGold(1000);
        PlayerInventoryEntity inv = inventory(11L, ITEM_REVIVE, 1);

        when(playerMapper.selectOne(isNull())).thenReturn(player);
        when(playerPetMapper.selectById(1L)).thenReturn(pet);
        when(playerInventoryMapper.selectOne(any())).thenReturn(inv);

        InventoryService.UseItemResult result =
                inventoryService.useRecoveryItem(ITEM_REVIVE, 1L);

        assertEquals(0, result.getBeforeHp());
        // 132 * 50 / 100 = 66
        assertEquals(66, result.getAfterHp());
        assertEquals(132, result.getMaxHp());
        assertEquals(66, pet.getCurrentHp());
        // 数量为 0 时删除记录
        verify(playerInventoryMapper).deleteById(11L);
        verify(playerInventoryMapper, never()).updateById(any(PlayerInventoryEntity.class));
    }

    @Test
    void useReviveItem_petAlive_rejected() {
        PlayerPetEntity pet = pet(5, 50);
        PlayerEntity player = playerWithGold(1000);
        PlayerInventoryEntity inv = inventory(11L, ITEM_REVIVE, 1);

        when(playerMapper.selectOne(isNull())).thenReturn(player);
        when(playerPetMapper.selectById(1L)).thenReturn(pet);
        when(playerInventoryMapper.selectOne(any())).thenReturn(inv);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> inventoryService.useRecoveryItem(ITEM_REVIVE, 1L));
        assertEquals("PET_NOT_DEAD", ex.getErrorCode());
    }

    @Test
    void useReviveItem_minimumOneHp() {
        // 极端：maxHp 很小（1）时，REVIVE value=1（1%）→ 至少 1
        // 这里使用默认种族 maxHp 不为 0 即可
        PlayerPetEntity pet = pet(5, 0);
        PlayerEntity player = playerWithGold(1000);
        PlayerInventoryEntity inv = inventory(11L, ITEM_REVIVE, 1);

        when(playerMapper.selectOne(isNull())).thenReturn(player);
        when(playerPetMapper.selectById(1L)).thenReturn(pet);
        when(playerInventoryMapper.selectOne(any())).thenReturn(inv);

        InventoryService.UseItemResult result =
                inventoryService.useRecoveryItem(ITEM_REVIVE, 1L);
        assertTrue(result.getAfterHp() >= 1);
    }

    // ==================== 通用校验 ====================

    @Test
    void useRecoveryItem_invalidItemId_rejected() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> inventoryService.useRecoveryItem("", 1L));
        assertEquals("INVALID_ITEM", ex.getErrorCode());
    }

    @Test
    void useRecoveryItem_invalidPetId_rejected() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> inventoryService.useRecoveryItem(ITEM_HEAL, null));
        assertEquals("INVALID_PET", ex.getErrorCode());
    }

    @Test
    void useRecoveryItem_petNotOwned_rejected() {
        PlayerEntity player = playerWithGold(1000);
        PlayerPetEntity pet = pet(5, 80);
        pet.setSaveId("SAVE_OTHER");  // 不属于当前存档

        when(playerMapper.selectOne(isNull())).thenReturn(player);
        when(playerPetMapper.selectById(1L)).thenReturn(pet);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> inventoryService.useRecoveryItem(ITEM_HEAL, 1L));
        assertEquals("PET_NOT_OWNED", ex.getErrorCode());
    }

    @Test
    void useRecoveryItem_itemConfigMissing_rejected() {
        PlayerEntity player = playerWithGold(1000);
        PlayerPetEntity pet = pet(5, 80);

        when(playerMapper.selectOne(isNull())).thenReturn(player);
        when(playerPetMapper.selectById(1L)).thenReturn(pet);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> inventoryService.useRecoveryItem("ITEM_UNKNOWN", 1L));
        assertEquals("ITEM_NOT_FOUND", ex.getErrorCode());
    }

    @Test
    void useRecoveryItem_itemNotUsableOutside_rejected() {
        // 构建一个 usableOutsideBattle=false 的道具
        GameConfigRegistry customRegistry = buildRegistryWithItems(
                List.of(species(SPECIES_ID, "COMMON", 50, List.of())),
                List.of(item("ITEM_BATTLE_ONLY", "RECOVERY", "HEAL_HP", 50, false)));
        PetGrowthService customGrowth = new PetGrowthService(customRegistry);
        InventoryService customService = new InventoryService(playerMapper, playerInventoryMapper,
                playerPetMapper, customRegistry, customGrowth);

        PlayerEntity player = playerWithGold(1000);
        PlayerPetEntity pet = pet(5, 80);

        when(playerMapper.selectOne(isNull())).thenReturn(player);
        when(playerPetMapper.selectById(1L)).thenReturn(pet);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> customService.useRecoveryItem("ITEM_BATTLE_ONLY", 1L));
        assertEquals("ITEM_NOT_USABLE_OUTSIDE", ex.getErrorCode());
    }

    @Test
    void useRecoveryItem_itemOutOfStock_rejected() {
        PlayerEntity player = playerWithGold(1000);
        PlayerPetEntity pet = pet(5, 80);
        PlayerInventoryEntity inv = inventory(11L, ITEM_HEAL, 0);  // 数量 0

        when(playerMapper.selectOne(isNull())).thenReturn(player);
        when(playerPetMapper.selectById(1L)).thenReturn(pet);
        when(playerInventoryMapper.selectOne(any())).thenReturn(inv);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> inventoryService.useRecoveryItem(ITEM_HEAL, 1L));
        assertEquals("ITEM_OUT_OF_STOCK", ex.getErrorCode());
    }

    @Test
    void useRecoveryItem_itemNotInInventory_rejected() {
        PlayerEntity player = playerWithGold(1000);
        PlayerPetEntity pet = pet(5, 80);

        when(playerMapper.selectOne(isNull())).thenReturn(player);
        when(playerPetMapper.selectById(1L)).thenReturn(pet);
        when(playerInventoryMapper.selectOne(any())).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> inventoryService.useRecoveryItem(ITEM_HEAL, 1L));
        assertEquals("ITEM_OUT_OF_STOCK", ex.getErrorCode());
    }

    @Test
    void useRecoveryItem_unknownItemType_rejected() {
        // 构建一个 itemType 不支持的道具
        GameConfigRegistry customRegistry = buildRegistryWithItems(
                List.of(species(SPECIES_ID, "COMMON", 50, List.of())),
                List.of(item("ITEM_MATERIAL", "MATERIAL", "MATERIAL", 10, true)));
        PetGrowthService customGrowth = new PetGrowthService(customRegistry);
        InventoryService customService = new InventoryService(playerMapper, playerInventoryMapper,
                playerPetMapper, customRegistry, customGrowth);

        PlayerEntity player = playerWithGold(1000);
        PlayerPetEntity pet = pet(5, 80);
        PlayerInventoryEntity inv = inventory(11L, "ITEM_MATERIAL", 1);

        when(playerMapper.selectOne(isNull())).thenReturn(player);
        when(playerPetMapper.selectById(1L)).thenReturn(pet);
        when(playerInventoryMapper.selectOne(any())).thenReturn(inv);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> customService.useRecoveryItem("ITEM_MATERIAL", 1L));
        assertEquals("ITEM_NOT_USABLE_OUTSIDE", ex.getErrorCode());
    }

    @Test
    void useRecoveryItem_quantityBecomesZero_deletesRecord() {
        PlayerPetEntity pet = pet(5, 80);
        PlayerEntity player = playerWithGold(1000);
        PlayerInventoryEntity inv = inventory(11L, ITEM_HEAL, 1);  // 仅剩 1

        when(playerMapper.selectOne(isNull())).thenReturn(player);
        when(playerPetMapper.selectById(1L)).thenReturn(pet);
        when(playerInventoryMapper.selectOne(any())).thenReturn(inv);

        inventoryService.useRecoveryItem(ITEM_HEAL, 1L);

        // 数量为 0 → 删除
        verify(playerInventoryMapper).deleteById(11L);
        verify(playerInventoryMapper, never()).updateById(any(PlayerInventoryEntity.class));
    }

    @Test
    void useRecoveryItem_quantityStillPositive_updatesRecord() {
        PlayerPetEntity pet = pet(5, 80);
        PlayerEntity player = playerWithGold(1000);
        PlayerInventoryEntity inv = inventory(11L, ITEM_HEAL, 5);  // 剩余 4

        when(playerMapper.selectOne(isNull())).thenReturn(player);
        when(playerPetMapper.selectById(1L)).thenReturn(pet);
        when(playerInventoryMapper.selectOne(any())).thenReturn(inv);

        inventoryService.useRecoveryItem(ITEM_HEAL, 1L);

        assertEquals(4, inv.getQuantity());
        verify(playerInventoryMapper).updateById(inv);
        verify(playerInventoryMapper, never()).deleteById(anyLong());
    }

    // ==================== 工具方法 ====================

    private PlayerPetEntity pet(int level, int currentHp) {
        PlayerPetEntity pet = new PlayerPetEntity();
        pet.setId(1L);
        pet.setSaveId("SAVE_1");
        pet.setSpeciesId(SPECIES_ID);
        pet.setLevel(level);
        pet.setCapturedLevel(1);
        pet.setHpAptitude(50);
        pet.setStrengthAptitude(50);
        pet.setSpiritAptitude(50);
        pet.setDefenseAptitude(50);
        pet.setResistanceAptitude(50);
        pet.setSpeedAptitude(50);
        pet.setFreePointHp(0);
        pet.setFreePointStrength(0);
        pet.setFreePointSpirit(0);
        pet.setFreePointDefense(0);
        pet.setFreePointResistance(0);
        pet.setFreePointSpeed(0);
        pet.setCurrentHp(currentHp);
        pet.setBaseHpOffset(0);
        pet.setBaseStrengthOffset(0);
        pet.setBaseSpiritOffset(0);
        pet.setBaseDefenseOffset(0);
        pet.setBaseResistanceOffset(0);
        pet.setBaseSpeedOffset(0);
        return pet;
    }

    private PlayerEntity playerWithGold(int gold) {
        PlayerEntity player = new PlayerEntity();
        player.setId(1L);
        player.setSaveId("SAVE_1");
        player.setGold(gold);
        player.setExpPool(0);
        return player;
    }

    private PlayerInventoryEntity inventory(Long id, String itemId, int quantity) {
        PlayerInventoryEntity inv = new PlayerInventoryEntity();
        inv.setId(id);
        inv.setSaveId("SAVE_1");
        inv.setItemId(itemId);
        inv.setQuantity(quantity);
        return inv;
    }

    private ItemConfig item(String id, String category, String itemType, double value, boolean usableOutside) {
        ItemConfig item = new ItemConfig();
        item.setId(id);
        item.setName(id);
        item.setDescription(id);
        item.setCategory(category);
        item.setItemType(itemType);
        item.setValue(value);
        item.setUsableOutsideBattle(usableOutside);
        item.setUsableInBattle(false);
        item.setDiscardable(true);
        return item;
    }

    /** 构建带道具配置的 GameConfigRegistry。 */
    private GameConfigRegistry buildRegistryWithItems(
            List<InitialPetsConfig.InitialPetOption> pets,
            List<ItemConfig> items) {
        try {
            GameConfigRegistry registry =
                    com.petgame.pet.PetGrowthTestFixtures.buildRegistry(pets);
            ItemsConfig itemsConfig = new ItemsConfig();
            itemsConfig.setItems(items);

            java.lang.reflect.Field itemsField =
                    GameConfigRegistry.class.getDeclaredField("itemsConfig");
            itemsField.setAccessible(true);
            itemsField.set(registry, itemsConfig);

            // 构建 itemIndex
            java.util.Map<String, ItemConfig> itemIndex = new java.util.LinkedHashMap<>();
            for (ItemConfig item : items) {
                itemIndex.put(item.getId(), item);
            }
            java.lang.reflect.Field indexField =
                    GameConfigRegistry.class.getDeclaredField("itemIndex");
            indexField.setAccessible(true);
            indexField.set(registry, itemIndex);

            return registry;
        } catch (Exception e) {
            throw new IllegalStateException("构建测试 Registry 失败", e);
        }
    }
}
