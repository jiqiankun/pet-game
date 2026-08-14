package com.petgame.shop;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.petgame.common.BusinessException;
import com.petgame.config.GameConfigRegistry;
import com.petgame.config.model.ItemConfig;
import com.petgame.config.model.ItemsConfig;
import com.petgame.config.model.QuestsConfig;
import com.petgame.config.model.ShopConfig;
import com.petgame.inventory.entity.PlayerInventoryEntity;
import com.petgame.inventory.mapper.PlayerInventoryMapper;
import com.petgame.player.entity.PlayerEntity;
import com.petgame.player.mapper.PlayerMapper;
import com.petgame.quest.entity.PlayerQuestEntity;
import com.petgame.quest.mapper.PlayerQuestMapper;
import com.petgame.shop.service.ShopService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 商店系统单元测试（阶段 10）。
 * <p>
 * 验证：购买流程（金币充足/不足）、解锁校验、背包增加。
 */
class ShopServiceTest {

    private PlayerMapper playerMapper;
    private PlayerInventoryMapper inventoryMapper;
    private PlayerQuestMapper questMapper;
    private GameConfigRegistry registry;
    private ShopService shopService;

    private PlayerEntity player;
    private ShopConfig shopConfig;

    @BeforeEach
    void setUp() {
        playerMapper = mock(PlayerMapper.class);
        inventoryMapper = mock(PlayerInventoryMapper.class);
        questMapper = mock(PlayerQuestMapper.class);
        registry = mock(GameConfigRegistry.class);

        player = new PlayerEntity();
        player.setSaveId("SAVE_001");
        player.setPlayerName("TestPlayer");
        player.setGold(1000);
        when(playerMapper.selectOne(any())).thenReturn(player);

        // 道具配置
        ItemConfig ballConfig = new ItemConfig();
        ballConfig.setId("ITEM_CAPTURE_BALL_NORMAL");
        ballConfig.setName("普通捕捉球");
        ballConfig.setDescription("基础捕捉球");
        ballConfig.setCategory("CAPTURE");
        ballConfig.setItemType("CAPTURE_BALL");
        ballConfig.setPrice(50);

        ItemConfig potionConfig = new ItemConfig();
        potionConfig.setId("ITEM_POTION_SMALL");
        potionConfig.setName("小型恢复药");
        potionConfig.setDescription("恢复少量HP");
        potionConfig.setCategory("RECOVERY");
        potionConfig.setItemType("HEAL_HP");
        potionConfig.setPrice(30);

        when(registry.getItem("ITEM_CAPTURE_BALL_NORMAL")).thenReturn(ballConfig);
        when(registry.getItem("ITEM_POTION_SMALL")).thenReturn(potionConfig);

        // 商店配置
        shopConfig = new ShopConfig();
        ShopConfig.ShopItemConfig ballShopItem = new ShopConfig.ShopItemConfig();
        ballShopItem.setItemId("ITEM_CAPTURE_BALL_NORMAL");
        ballShopItem.setPrice(50);

        ShopConfig.ShopItemConfig potionShopItem = new ShopConfig.ShopItemConfig();
        potionShopItem.setItemId("ITEM_POTION_SMALL");
        potionShopItem.setPrice(30);
        potionShopItem.setUnlockQuestId("QUEST_MAIN_03");

        shopConfig.setShopItems(List.of(ballShopItem, potionShopItem));
        when(registry.getShopConfig()).thenReturn(shopConfig);

        shopService = new ShopService(playerMapper, inventoryMapper, questMapper, registry);
    }

    @Test
    void getShopView_shouldReturnItems() {
        ShopService.ShopView view = shopService.getShopView();
        assertNotNull(view);
        assertEquals(1000, view.getGold());
        assertEquals(2, view.getItems().size());
    }

    @Test
    void buyItem_goldSufficient_shouldSucceed() {
        when(inventoryMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        ShopService.BuyResult result = shopService.buyItem("ITEM_CAPTURE_BALL_NORMAL", 2);
        assertNotNull(result);
        assertEquals("ITEM_CAPTURE_BALL_NORMAL", result.getItemId());
        assertEquals(2, result.getQuantity());
        assertEquals(100, result.getTotalCost());
        assertEquals(900, result.getRemainingGold());
        verify(playerMapper).updateById(player);
        verify(inventoryMapper).insert(any(PlayerInventoryEntity.class));
    }

    @Test
    void buyItem_goldInsufficient_shouldThrow() {
        player.setGold(10);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> shopService.buyItem("ITEM_CAPTURE_BALL_NORMAL", 5));
        assertEquals("GOLD_NOT_ENOUGH", ex.getErrorCode());
    }

    @Test
    void buyItem_questNotCompleted_shouldThrow() {
        when(questMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> shopService.buyItem("ITEM_POTION_SMALL", 1));
        assertEquals("SHOP_ITEM_LOCKED", ex.getErrorCode());
    }

    @Test
    void buyItem_questCompleted_shouldSucceed() {
        PlayerQuestEntity completedQuest = new PlayerQuestEntity();
        completedQuest.setStatus("COMPLETED");
        when(questMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(completedQuest);
        when(inventoryMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        ShopService.BuyResult result = shopService.buyItem("ITEM_POTION_SMALL", 1);
        assertNotNull(result);
        assertEquals(30, result.getTotalCost());
    }

    @Test
    void buyItem_notInShop_shouldThrow() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> shopService.buyItem("ITEM_NOT_IN_SHOP", 1));
        assertEquals("ITEM_NOT_IN_SHOP", ex.getErrorCode());
    }

    @Test
    void buyItem_existingInventory_shouldAccumulate() {
        PlayerInventoryEntity existing = new PlayerInventoryEntity();
        existing.setId(1L);
        existing.setSaveId("SAVE_001");
        existing.setItemId("ITEM_CAPTURE_BALL_NORMAL");
        existing.setQuantity(5);
        when(inventoryMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);

        ShopService.BuyResult result = shopService.buyItem("ITEM_CAPTURE_BALL_NORMAL", 3);
        assertEquals(8, existing.getQuantity());
        verify(inventoryMapper).updateById(existing);
    }
}
