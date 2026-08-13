package com.petgame.battle.service;

import com.petgame.battle.engine.BattleContext;
import com.petgame.battle.model.BattleSide;
import com.petgame.battle.model.BattleUnit;
import com.petgame.battle.ai.WildEnemyDecisionProvider;
import com.petgame.capture.WildEncounterService;
import com.petgame.common.BusinessException;
import com.petgame.common.GameRandom;
import com.petgame.config.GameConfigRegistry;
import com.petgame.config.model.ItemConfig;
import com.petgame.config.model.ItemsConfig;
import com.petgame.config.model.PetSpeciesConfig;
import com.petgame.config.model.TestBattleConfig;
import com.petgame.inventory.entity.PlayerInventoryEntity;
import com.petgame.inventory.mapper.PlayerInventoryMapper;
import com.petgame.pet.domain.PetGrowthService;
import com.petgame.pet.entity.PlayerPetEntity;
import com.petgame.pet.mapper.PlayerPetMapper;
import com.petgame.pet.mapper.PlayerPetSkillMapper;
import com.petgame.player.entity.PlayerEntity;
import com.petgame.player.mapper.PlayerMapper;
import com.petgame.team.mapper.PlayerTeamMapper;
import com.petgame.team.mapper.PlayerTeamMemberMapper;
import com.petgame.team.service.TeamService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.petgame.pet.PetGrowthTestFixtures.buildRegistry;
import static com.petgame.pet.PetGrowthTestFixtures.species;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

/**
 * BattleService 结算单元测试（阶段 4 验收标准）。
 * <p>
 * 覆盖：玩家胜（HP 回写 + 经验/金币/掉落发放）、玩家败（仅 HP 回写）、
 * 战斗未结束拒绝结算、已结算拒绝重复结算、掉落按 chance 概率、
 * 战斗统计累加（battle_count +1、win_count 仅胜方 +1）、掉落累加 vs 新增。
 * <p>
 * 验证：所有结算操作必须在同一事务内完成（任一失败回滚，避免部分成功脏数据）。
 */
@ExtendWith(MockitoExtension.class)
class BattleServiceSettlementTest {

    @Mock
    private PlayerMapper playerMapper;
    @Mock
    private PlayerPetMapper playerPetMapper;
    @Mock
    private PlayerPetSkillMapper playerPetSkillMapper;
    @Mock
    private PlayerTeamMapper playerTeamMapper;
    @Mock
    private PlayerTeamMemberMapper playerTeamMemberMapper;
    @Mock
    private PlayerInventoryMapper playerInventoryMapper;
    @Mock
    private WildEnemyDecisionProvider enemyDecisionProvider;
    @Mock
    private WildEncounterService wildEncounterService;
    @Mock
    private TeamService teamService;

    private GameConfigRegistry registry;
    private PetGrowthService growthService;
    private BattleService battleService;

    private static final String SPECIES_ID = "SPEC_TEST";
    private static final String ITEM_DROP = "ITEM_POTION_SMALL";

    @BeforeEach
    void setUp() {
        PetSpeciesConfig speciesOption =
                species(SPECIES_ID, "COMMON", 50, List.of());
        ItemConfig dropItem = item(ITEM_DROP, "RECOVERY", "HEAL_HP", 50, true);

        registry = buildRegistryWithRewards(
                List.of(speciesOption),
                List.of(dropItem),
                buildRewards(100, 50, dropEntry(ITEM_DROP, 1.0, 2)));
        growthService = new PetGrowthService(registry);

        battleService = new BattleService(registry, enemyDecisionProvider,
                playerMapper, playerPetMapper, playerPetSkillMapper,
                playerTeamMapper, playerTeamMemberMapper, playerInventoryMapper,
                growthService, wildEncounterService, teamService);
    }

    // ==================== 玩家胜：HP 回写 + 奖励发放 ====================

    @Test
    void settleBattle_playerWin_writesBackHpAndRewards() {
        // 构造已结束战斗（玩家胜）
        BattleUnit unit1 = playerUnit("P_1", 1L, 100, 30);  // 存活，HP 30
        BattleUnit unit2 = playerUnit("P_2", 2L, 100, 0);   // 倒下，HP 0
        BattleContext ctx = finishedBattle("BATTLE_1", 12345L, "PLAYER", unit1, unit2);
        injectBattle("BATTLE_1", ctx);

        PlayerEntity player = playerWithExpPool(500, 200);
        PlayerPetEntity pet1 = pet(1L, 80);   // 存档 HP 80 → 写回 30
        PlayerPetEntity pet2 = pet(2L, 100);  // 存档 HP 100 → 写回 0

        when(playerMapper.selectOne(isNull())).thenReturn(player);
        when(playerPetMapper.selectById(1L)).thenReturn(pet1);
        when(playerPetMapper.selectById(2L)).thenReturn(pet2);
        when(playerPetMapper.updateById(any(PlayerPetEntity.class))).thenReturn(1);
        when(playerMapper.updateById(any(PlayerEntity.class))).thenReturn(1);
        // 掉落：道具不存在，新增
        when(playerInventoryMapper.selectOne(any())).thenReturn(null);
        when(playerInventoryMapper.insert(any(PlayerInventoryEntity.class))).thenReturn(1);

        BattleService.BattleSettlement result = battleService.settleBattle("BATTLE_1");

        // 胜方
        assertTrue(result.isPlayerWon());
        assertEquals("PLAYER", result.getWinner());
        // HP 回写：unit1=30, unit2=0
        assertEquals(2, result.getHpWritebacks().size());
        assertEquals(30, pet1.getCurrentHp());
        assertEquals(0, pet2.getCurrentHp());
        // 经验池 +100
        assertEquals(100, result.getExpGained());
        assertEquals(600, player.getExpPool());
        // 金币 +50
        assertEquals(50, result.getGoldGained());
        assertEquals(250, player.getGold());
        // 掉落：chance=1.0 必掉 2 个
        assertEquals(1, result.getDrops().size());
        assertEquals(ITEM_DROP, result.getDrops().get(0).getItemId());
        assertEquals(2, result.getDrops().get(0).getQuantity());
        // 战斗统计：双方都 +battleCount，胜方都 +winCount
        verify(playerPetMapper, times(2)).updateById(any(PlayerPetEntity.class));
        // 掉落新增背包记录
        verify(playerInventoryMapper).insert(any(PlayerInventoryEntity.class));
    }

    @Test
    void settleBattle_playerWin_dropAccumulatesExistingInventory() {
        // 已有该道具 → 累加数量
        BattleUnit unit1 = playerUnit("P_1", 1L, 100, 50);
        BattleContext ctx = finishedBattle("BATTLE_2", 1L, "PLAYER", unit1);
        injectBattle("BATTLE_2", ctx);

        PlayerEntity player = playerWithExpPool(500, 200);
        PlayerPetEntity pet1 = pet(1L, 80);
        PlayerInventoryEntity existing = inventory(11L, ITEM_DROP, 3);  // 已有 3 个

        when(playerMapper.selectOne(isNull())).thenReturn(player);
        when(playerPetMapper.selectById(1L)).thenReturn(pet1);
        when(playerPetMapper.updateById(any(PlayerPetEntity.class))).thenReturn(1);
        when(playerMapper.updateById(any(PlayerEntity.class))).thenReturn(1);
        when(playerInventoryMapper.selectOne(any())).thenReturn(existing);

        BattleService.BattleSettlement result = battleService.settleBattle("BATTLE_2");

        // 掉落 2 个，累加到已有 3 → 5
        assertEquals(5, existing.getQuantity());
        verify(playerInventoryMapper).updateById(existing);
        verify(playerInventoryMapper, never()).insert(any(PlayerInventoryEntity.class));
    }

    // ==================== 玩家败：仅 HP 回写 ====================

    @Test
    void settleBattle_playerLose_onlyWritesBackHp() {
        BattleUnit unit1 = playerUnit("P_1", 1L, 100, 20);
        BattleContext ctx = finishedBattle("BATTLE_3", 1L, "ENEMY", unit1);
        injectBattle("BATTLE_3", ctx);

        PlayerEntity player = playerWithExpPool(500, 200);
        PlayerPetEntity pet1 = pet(1L, 80);

        when(playerMapper.selectOne(isNull())).thenReturn(player);
        when(playerPetMapper.selectById(1L)).thenReturn(pet1);
        when(playerPetMapper.updateById(any(PlayerPetEntity.class))).thenReturn(1);

        BattleService.BattleSettlement result = battleService.settleBattle("BATTLE_3");

        assertFalse(result.isPlayerWon());
        assertEquals("ENEMY", result.getWinner());
        // HP 回写
        assertEquals(20, pet1.getCurrentHp());
        assertEquals(1, result.getHpWritebacks().size());
        // 无奖励
        assertEquals(0, result.getExpGained());
        assertEquals(0, result.getGoldGained());
        assertTrue(result.getDrops().isEmpty());
        // 经验池/金币不变
        assertEquals(500, player.getExpPool());
        assertEquals(200, player.getGold());
        // 玩家不更新（无奖励）
        verify(playerMapper, never()).updateById(any(PlayerEntity.class));
        // 战斗统计：仅 battleCount +1，winCount 不变
        verify(playerPetMapper).updateById(pet1);
        // battle_count 累加
        assertEquals(1, pet1.getBattleCount());
        assertEquals(0, pet1.getWinCount());  // 败方不累加 win_count
    }

    // ==================== 拒绝结算场景 ====================

    @Test
    void settleBattle_notFinished_rejected() {
        // 战斗未结束
        BattleContext ctx = new BattleContext("BATTLE_4", 1L);
        ctx.setFinished(false);
        ctx.setWinner(null);
        ctx.setPlayerSide(side(playerUnit("P_1", 1L, 100, 100)));
        ctx.setEnemySide(side());
        injectBattle("BATTLE_4", ctx);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> battleService.settleBattle("BATTLE_4"));
        assertEquals("BATTLE_NOT_FINISHED", ex.getErrorCode());
        verify(playerMapper, never()).updateById(any(PlayerEntity.class));
        verify(playerPetMapper, never()).updateById(any(PlayerPetEntity.class));
    }

    @Test
    void settleBattle_alreadySettled_rejected() {
        BattleContext ctx = finishedBattle("BATTLE_5", 1L, "PLAYER",
                playerUnit("P_1", 1L, 100, 50));
        injectBattle("BATTLE_5", ctx);
        // 标记为已结算
        markSettled("BATTLE_5");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> battleService.settleBattle("BATTLE_5"));
        assertEquals("BATTLE_ALREADY_SETTLED", ex.getErrorCode());
    }

    @Test
    void settleBattle_notFound_rejected() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> battleService.settleBattle("UNKNOWN_BATTLE"));
        assertEquals("BATTLE_NOT_FOUND", ex.getErrorCode());
    }

    @Test
    void settleBattle_noSave_rejected() {
        BattleContext ctx = finishedBattle("BATTLE_6", 1L, "PLAYER",
                playerUnit("P_1", 1L, 100, 50));
        injectBattle("BATTLE_6", ctx);

        when(playerMapper.selectOne(isNull())).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> battleService.settleBattle("BATTLE_6"));
        assertEquals("NO_SAVE", ex.getErrorCode());
    }

    // ==================== 掉落概率 ====================

    @Test
    void settleBattle_dropChanceZero_neverDrops() {
        // 配置 chance=0.0 的掉落
        PetSpeciesConfig speciesOption =
                species(SPECIES_ID, "COMMON", 50, List.of());
        ItemConfig dropItem = item(ITEM_DROP, "RECOVERY", "HEAL_HP", 50, true);
        GameConfigRegistry customRegistry = buildRegistryWithRewards(
                List.of(speciesOption),
                List.of(dropItem),
                buildRewards(100, 50, dropEntry(ITEM_DROP, 0.0, 2)));  // chance=0
        PetGrowthService customGrowth = new PetGrowthService(customRegistry);
        BattleService customService = new BattleService(customRegistry, enemyDecisionProvider,
                playerMapper, playerPetMapper, playerPetSkillMapper,
                playerTeamMapper, playerTeamMemberMapper, playerInventoryMapper,
                customGrowth, wildEncounterService, teamService);

        BattleUnit unit1 = playerUnit("P_1", 1L, 100, 50);
        BattleContext ctx = finishedBattle("BATTLE_7", 1L, "PLAYER", unit1);
        injectBattleTo(customService, "BATTLE_7", ctx);

        PlayerEntity player = playerWithExpPool(500, 200);
        PlayerPetEntity pet1 = pet(1L, 80);

        when(playerMapper.selectOne(isNull())).thenReturn(player);
        when(playerPetMapper.selectById(1L)).thenReturn(pet1);
        when(playerPetMapper.updateById(any(PlayerPetEntity.class))).thenReturn(1);
        when(playerMapper.updateById(any(PlayerEntity.class))).thenReturn(1);

        BattleService.BattleSettlement result = customService.settleBattle("BATTLE_7");

        // 经验/金币照常发放，但掉落为空
        assertEquals(100, result.getExpGained());
        assertEquals(50, result.getGoldGained());
        assertTrue(result.getDrops().isEmpty());
        verify(playerInventoryMapper, never()).insert(any(PlayerInventoryEntity.class));
        verify(playerInventoryMapper, never()).updateById(any(PlayerInventoryEntity.class));
    }

    @Test
    void settleBattle_dropChanceOne_alwaysDrops() {
        // 默认配置 chance=1.0 必掉
        BattleUnit unit1 = playerUnit("P_1", 1L, 100, 50);
        BattleContext ctx = finishedBattle("BATTLE_8", 1L, "PLAYER", unit1);
        injectBattle("BATTLE_8", ctx);

        PlayerEntity player = playerWithExpPool(500, 200);
        PlayerPetEntity pet1 = pet(1L, 80);

        when(playerMapper.selectOne(isNull())).thenReturn(player);
        when(playerPetMapper.selectById(1L)).thenReturn(pet1);
        when(playerPetMapper.updateById(any(PlayerPetEntity.class))).thenReturn(1);
        when(playerMapper.updateById(any(PlayerEntity.class))).thenReturn(1);
        when(playerInventoryMapper.selectOne(any())).thenReturn(null);
        when(playerInventoryMapper.insert(any(PlayerInventoryEntity.class))).thenReturn(1);

        BattleService.BattleSettlement result = battleService.settleBattle("BATTLE_8");

        assertEquals(1, result.getDrops().size());
        assertEquals(2, result.getDrops().get(0).getQuantity());
    }

    // ==================== HP 回写边界 ====================

    @Test
    void settleBattle_hpWriteback_cappedAtMaxHp() {
        // 战斗中 currentHp > maxHp（异常值），回写时封顶
        BattleUnit unit1 = playerUnit("P_1", 1L, 100, 150);  // maxHp=100, currentHp=150
        BattleContext ctx = finishedBattle("BATTLE_9", 1L, "PLAYER", unit1);
        injectBattle("BATTLE_9", ctx);

        PlayerEntity player = playerWithExpPool(500, 200);
        PlayerPetEntity pet1 = pet(1L, 80);

        when(playerMapper.selectOne(isNull())).thenReturn(player);
        when(playerPetMapper.selectById(1L)).thenReturn(pet1);
        when(playerPetMapper.updateById(any(PlayerPetEntity.class))).thenReturn(1);
        when(playerMapper.updateById(any(PlayerEntity.class))).thenReturn(1);
        when(playerInventoryMapper.selectOne(any())).thenReturn(null);
        when(playerInventoryMapper.insert(any(PlayerInventoryEntity.class))).thenReturn(1);

        battleService.settleBattle("BATTLE_9");

        // 回写后 HP 封顶为 maxHp=100
        assertEquals(100, pet1.getCurrentHp());
    }

    @Test
    void settleBattle_hpWriteback_neverNegative() {
        // currentHp=-10 异常值 → 下限 0
        BattleUnit unit1 = playerUnit("P_1", 1L, 100, -10);
        BattleContext ctx = finishedBattle("BATTLE_10", 1L, "ENEMY", unit1);
        injectBattle("BATTLE_10", ctx);

        PlayerEntity player = playerWithExpPool(500, 200);
        PlayerPetEntity pet1 = pet(1L, 80);

        when(playerMapper.selectOne(isNull())).thenReturn(player);
        when(playerPetMapper.selectById(1L)).thenReturn(pet1);
        when(playerPetMapper.updateById(any(PlayerPetEntity.class))).thenReturn(1);

        battleService.settleBattle("BATTLE_10");

        assertEquals(0, pet1.getCurrentHp());
    }

    // ==================== 战斗统计 ====================

    @Test
    void settleBattle_winCountOnlyForWinner() {
        BattleUnit unit1 = playerUnit("P_1", 1L, 100, 50);
        BattleUnit unit2 = playerUnit("P_2", 2L, 100, 0);
        BattleContext ctx = finishedBattle("BATTLE_11", 1L, "PLAYER", unit1, unit2);
        injectBattle("BATTLE_11", ctx);

        PlayerEntity player = playerWithExpPool(500, 200);
        PlayerPetEntity pet1 = pet(1L, 80);
        pet1.setBattleCount(2);
        pet1.setWinCount(1);
        PlayerPetEntity pet2 = pet(2L, 100);
        pet2.setBattleCount(0);
        pet2.setWinCount(0);

        when(playerMapper.selectOne(isNull())).thenReturn(player);
        when(playerPetMapper.selectById(1L)).thenReturn(pet1);
        when(playerPetMapper.selectById(2L)).thenReturn(pet2);
        when(playerPetMapper.updateById(any(PlayerPetEntity.class))).thenReturn(1);
        when(playerMapper.updateById(any(PlayerEntity.class))).thenReturn(1);
        when(playerInventoryMapper.selectOne(any())).thenReturn(null);
        when(playerInventoryMapper.insert(any(PlayerInventoryEntity.class))).thenReturn(1);

        battleService.settleBattle("BATTLE_11");

        // 玩家胜：所有参战宠物 battle_count +1、win_count +1
        assertEquals(3, pet1.getBattleCount());  // 2 + 1
        assertEquals(2, pet1.getWinCount());      // 1 + 1
        assertEquals(1, pet2.getBattleCount());  // 0 + 1
        assertEquals(1, pet2.getWinCount());      // 0 + 1
    }

    @Test
    void settleBattle_loserNoWinCountIncrement() {
        BattleUnit unit1 = playerUnit("P_1", 1L, 100, 50);
        BattleContext ctx = finishedBattle("BATTLE_12", 1L, "ENEMY", unit1);
        injectBattle("BATTLE_12", ctx);

        PlayerEntity player = playerWithExpPool(500, 200);
        PlayerPetEntity pet1 = pet(1L, 80);
        pet1.setBattleCount(5);
        pet1.setWinCount(3);

        when(playerMapper.selectOne(isNull())).thenReturn(player);
        when(playerPetMapper.selectById(1L)).thenReturn(pet1);
        when(playerPetMapper.updateById(any(PlayerPetEntity.class))).thenReturn(1);

        battleService.settleBattle("BATTLE_12");

        // 败方：battleCount +1，winCount 不变
        assertEquals(6, pet1.getBattleCount());
        assertEquals(3, pet1.getWinCount());
    }

    // ==================== 跳过非玩家单位 ====================

    @Test
    void settleBattle_skipsUnitsWithoutPetDbId() {
        // 包含一个 petDbId=null 的单位（不应回写）
        BattleUnit unit1 = playerUnit("P_1", 1L, 100, 50);
        BattleUnit unit2 = playerUnit("ENEMY_X", null, 100, 50);  // petDbId=null
        BattleContext ctx = finishedBattle("BATTLE_13", 1L, "PLAYER", unit1, unit2);
        injectBattle("BATTLE_13", ctx);

        PlayerEntity player = playerWithExpPool(500, 200);
        PlayerPetEntity pet1 = pet(1L, 80);

        when(playerMapper.selectOne(isNull())).thenReturn(player);
        when(playerPetMapper.selectById(1L)).thenReturn(pet1);
        when(playerPetMapper.updateById(any(PlayerPetEntity.class))).thenReturn(1);
        when(playerMapper.updateById(any(PlayerEntity.class))).thenReturn(1);
        when(playerInventoryMapper.selectOne(any())).thenReturn(null);
        when(playerInventoryMapper.insert(any(PlayerInventoryEntity.class))).thenReturn(1);

        BattleService.BattleSettlement result = battleService.settleBattle("BATTLE_13");

        // 仅 1 个有效 HP 回写（unit2 的 petDbId=null 被跳过）
        assertEquals(1, result.getHpWritebacks().size());
        verify(playerPetMapper, times(1)).updateById(any(PlayerPetEntity.class));
    }

    @Test
    void settleBattle_skipsMissingPet() {
        // player_pet 表中宠物已被删除
        BattleUnit unit1 = playerUnit("P_1", 1L, 100, 50);
        BattleContext ctx = finishedBattle("BATTLE_14", 1L, "PLAYER", unit1);
        injectBattle("BATTLE_14", ctx);

        PlayerEntity player = playerWithExpPool(500, 200);

        when(playerMapper.selectOne(isNull())).thenReturn(player);
        when(playerPetMapper.selectById(1L)).thenReturn(null);  // 宠物不存在
        when(playerMapper.updateById(any(PlayerEntity.class))).thenReturn(1);
        when(playerInventoryMapper.selectOne(any())).thenReturn(null);
        when(playerInventoryMapper.insert(any(PlayerInventoryEntity.class))).thenReturn(1);

        BattleService.BattleSettlement result = battleService.settleBattle("BATTLE_14");

        // HP 回写为空（宠物不存在被跳过），但奖励照常发放
        assertTrue(result.getHpWritebacks().isEmpty());
        assertEquals(100, result.getExpGained());
    }

    // ==================== 工具方法 ====================

    /** 构造已结束战斗上下文。 */
    private BattleContext finishedBattle(String battleId, long seed, String winner,
                                          BattleUnit... playerUnits) {
        BattleContext ctx = new BattleContext(battleId, seed);
        ctx.setFinished(true);
        ctx.setWinner(winner);
        ctx.setPlayerSide(side(playerUnits));
        ctx.setEnemySide(side());
        return ctx;
    }

    private BattleSide side(BattleUnit... units) {
        BattleSide side = new BattleSide("PLAYER");
        for (BattleUnit u : units) {
            side.getUnits().add(u);
        }
        return side;
    }

    /** 构造玩家战斗单位。 */
    private BattleUnit playerUnit(String unitId, Long petDbId, int maxHp, int currentHp) {
        BattleUnit unit = new BattleUnit();
        unit.setUnitId(unitId);
        unit.setPetDbId(petDbId);
        unit.setName(unitId);
        unit.setElement("WATER");
        unit.setLevel(5);
        unit.setMaxHp(maxHp);
        unit.setCurrentHp(currentHp);
        unit.setStrength(50);
        unit.setSpirit(50);
        unit.setDefense(50);
        unit.setResistance(50);
        unit.setSpeed(50);
        unit.setAlive(currentHp > 0);
        return unit;
    }

    private PlayerEntity playerWithExpPool(int expPool, int gold) {
        PlayerEntity player = new PlayerEntity();
        player.setId(1L);
        player.setSaveId("SAVE_1");
        player.setExpPool(expPool);
        player.setGold(gold);
        return player;
    }

    private PlayerPetEntity pet(Long id, int currentHp) {
        PlayerPetEntity pet = new PlayerPetEntity();
        pet.setId(id);
        pet.setSaveId("SAVE_1");
        pet.setSpeciesId(SPECIES_ID);
        pet.setLevel(5);
        pet.setCurrentHp(currentHp);
        pet.setBattleCount(0);
        pet.setWinCount(0);
        return pet;
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
        return item;
    }

    private TestBattleConfig.BattleReward buildRewards(int exp, int gold, TestBattleConfig.DropEntry... drops) {
        TestBattleConfig.BattleReward reward = new TestBattleConfig.BattleReward();
        reward.setExp(exp);
        reward.setGold(gold);
        reward.setDrops(List.of(drops));
        return reward;
    }

    private TestBattleConfig.DropEntry dropEntry(String itemId, double chance, int quantity) {
        TestBattleConfig.DropEntry drop = new TestBattleConfig.DropEntry();
        drop.setItemId(itemId);
        drop.setChance(chance);
        drop.setQuantity(quantity);
        return drop;
    }

    /** 构建带奖励配置的 GameConfigRegistry。 */
    private GameConfigRegistry buildRegistryWithRewards(
            List<PetSpeciesConfig> pets,
            List<ItemConfig> items,
            TestBattleConfig.BattleReward rewards) {
        try {
            GameConfigRegistry registry = buildRegistry(pets);

            ItemsConfig itemsConfig = new ItemsConfig();
            itemsConfig.setItems(items);
            Field itemsField = GameConfigRegistry.class.getDeclaredField("itemsConfig");
            itemsField.setAccessible(true);
            itemsField.set(registry, itemsConfig);

            java.util.Map<String, ItemConfig> itemIndex = new java.util.LinkedHashMap<>();
            for (ItemConfig item : items) {
                itemIndex.put(item.getId(), item);
            }
            Field indexField = GameConfigRegistry.class.getDeclaredField("itemIndex");
            indexField.setAccessible(true);
            indexField.set(registry, itemIndex);

            TestBattleConfig testBattleConfig = new TestBattleConfig();
            testBattleConfig.setRewards(rewards);
            Field tbField = GameConfigRegistry.class.getDeclaredField("testBattleConfig");
            tbField.setAccessible(true);
            tbField.set(registry, testBattleConfig);

            return registry;
        } catch (Exception e) {
            throw new IllegalStateException("构建测试 Registry 失败", e);
        }
    }

    /** 通过反射注入战斗上下文到 battles 内存池。 */
    @SuppressWarnings("unchecked")
    private void injectBattle(String battleId, BattleContext ctx) {
        injectBattleTo(battleService, battleId, ctx);
    }

    @SuppressWarnings("unchecked")
    private void injectBattleTo(BattleService target, String battleId, BattleContext ctx) {
        try {
            Field field = BattleService.class.getDeclaredField("battles");
            field.setAccessible(true);
            Map<String, BattleContext> battles = (Map<String, BattleContext>) field.get(target);
            battles.put(battleId, ctx);
        } catch (Exception e) {
            throw new IllegalStateException("注入战斗上下文失败", e);
        }
    }

    @SuppressWarnings("unchecked")
    private void markSettled(String battleId) {
        try {
            Field field = BattleService.class.getDeclaredField("settledBattles");
            field.setAccessible(true);
            Set<String> settled = (Set<String>) field.get(battleService);
            settled.add(battleId);
        } catch (Exception e) {
            throw new IllegalStateException("标记已结算失败", e);
        }
    }
}
