package com.petgame.achievement;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.petgame.achievement.entity.PlayerAchievementEntity;
import com.petgame.achievement.mapper.PlayerAchievementMapper;
import com.petgame.achievement.service.AchievementService;
import com.petgame.boss.mapper.BossDefeatCountMapper;
import com.petgame.config.GameConfigRegistry;
import com.petgame.config.model.AchievementsConfig;
import com.petgame.inventory.mapper.PlayerInventoryMapper;
import com.petgame.map.mapper.PlayerRegionUnlockMapper;
import com.petgame.pet.mapper.PlayerPetMapper;
import com.petgame.player.entity.PlayerEntity;
import com.petgame.player.mapper.PlayerMapper;
import com.petgame.pokedex.service.PokedexService;
import com.petgame.quest.mapper.PlayerQuestMapper;
import com.petgame.statistics.service.StatisticsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 成就服务单元测试（阶段 11，需求 §110）。
 * <p>
 * 验证：基于玩家状态快照的条件评估（STAT_GE / MAIN_QUEST_COMPLETE）、
 * 解锁与奖励发放、隐藏成就列表过滤。
 */
class AchievementServiceTest {

    private GameConfigRegistry registry;
    private PlayerAchievementMapper achievementMapper;
    private PlayerMapper playerMapper;
    private PlayerInventoryMapper inventoryMapper;
    private PlayerPetMapper petMapper;
    private PlayerQuestMapper questMapper;
    private PlayerRegionUnlockMapper regionUnlockMapper;
    private BossDefeatCountMapper bossDefeatCountMapper;
    private StatisticsService statisticsService;
    private PokedexService pokedexService;
    private AchievementService achievementService;

    private PlayerEntity player;

    @BeforeEach
    void setUp() {
        registry = mock(GameConfigRegistry.class);
        achievementMapper = mock(PlayerAchievementMapper.class);
        playerMapper = mock(PlayerMapper.class);
        inventoryMapper = mock(PlayerInventoryMapper.class);
        petMapper = mock(PlayerPetMapper.class);
        questMapper = mock(PlayerQuestMapper.class);
        regionUnlockMapper = mock(PlayerRegionUnlockMapper.class);
        bossDefeatCountMapper = mock(BossDefeatCountMapper.class);
        statisticsService = mock(StatisticsService.class);
        pokedexService = mock(PokedexService.class);

        player = new PlayerEntity();
        player.setId(1L);
        player.setSaveId("SAVE_001");
        player.setGold(100);
        player.setExpPool(0);
        player.setStoryCompleted(false);
        when(playerMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(player);

        // 空状态快照默认值
        when(pokedexService.getFullPokedex("SAVE_001")).thenReturn(List.of());
        when(regionUnlockMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(bossDefeatCountMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(questMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(petMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(statisticsService.getAllStats("SAVE_001")).thenReturn(Map.of());
        when(achievementMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(achievementMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        achievementService = new AchievementService(registry, achievementMapper, playerMapper,
                inventoryMapper, petMapper, questMapper, regionUnlockMapper,
                bossDefeatCountMapper, statisticsService, pokedexService);
    }

    private void configureAchievements(AchievementsConfig config) {
        when(registry.getAchievementsConfig()).thenReturn(config);
    }

    @Test
    void checkAchievements_statGeMet_shouldUnlockAndReward() {
        AchievementsConfig cfg = new AchievementsConfig();
        AchievementsConfig.AchievementConfig ach = new AchievementsConfig.AchievementConfig();
        ach.setId("ACH_TEST_STAT");
        ach.setName("测试统计");
        ach.setConditionType("STAT_GE");
        ach.setConditionValue(StatisticsService.ST_BATTLES_WON);
        ach.setConditionCount(5);
        AchievementsConfig.RewardEntry reward = new AchievementsConfig.RewardEntry();
        reward.setType("GOLD");
        reward.setQuantity(100);
        ach.setRewards(List.of(reward));
        cfg.setAchievements(List.of(ach));
        configureAchievements(cfg);

        when(statisticsService.getAllStats("SAVE_001"))
                .thenReturn(Map.of(StatisticsService.ST_BATTLES_WON, 7L));

        achievementService.checkAchievements("SAVE_001");

        verify(achievementMapper).insert(org.mockito.ArgumentMatchers.<PlayerAchievementEntity>argThat(e ->
                "SAVE_001".equals(e.getSaveId()) && "ACH_TEST_STAT".equals(e.getAchievementId())));
        verify(statisticsService).increment("SAVE_001", StatisticsService.ST_GOLD_EARNED, 100);
        assertEquals(200, player.getGold());
        verify(playerMapper).updateById(player);
    }

    @Test
    void checkAchievements_statGeNotMet_shouldNotUnlock() {
        AchievementsConfig cfg = new AchievementsConfig();
        AchievementsConfig.AchievementConfig ach = new AchievementsConfig.AchievementConfig();
        ach.setId("ACH_TEST_STAT");
        ach.setConditionType("STAT_GE");
        ach.setConditionValue(StatisticsService.ST_BATTLES_WON);
        ach.setConditionCount(5);
        cfg.setAchievements(List.of(ach));
        configureAchievements(cfg);

        when(statisticsService.getAllStats("SAVE_001"))
                .thenReturn(Map.of(StatisticsService.ST_BATTLES_WON, 3L));

        achievementService.checkAchievements("SAVE_001");

        verify(achievementMapper, never()).insert(any(PlayerAchievementEntity.class));
    }

    @Test
    void checkAchievements_mainQuestComplete_shouldUnlock() {
        AchievementsConfig cfg = new AchievementsConfig();
        AchievementsConfig.AchievementConfig ach = new AchievementsConfig.AchievementConfig();
        ach.setId("ACH_TEST_MAIN");
        ach.setConditionType("MAIN_QUEST_COMPLETE");
        cfg.setAchievements(List.of(ach));
        configureAchievements(cfg);

        player.setStoryCompleted(true);
        achievementService.checkAchievements("SAVE_001");
        verify(achievementMapper).insert(any(PlayerAchievementEntity.class));
    }

    @Test
    void checkAchievements_alreadyUnlocked_shouldSkip() {
        AchievementsConfig cfg = new AchievementsConfig();
        AchievementsConfig.AchievementConfig ach = new AchievementsConfig.AchievementConfig();
        ach.setId("ACH_TEST_STAT");
        ach.setConditionType("STAT_GE");
        ach.setConditionValue(StatisticsService.ST_BATTLES_WON);
        ach.setConditionCount(1);
        cfg.setAchievements(List.of(ach));
        configureAchievements(cfg);

        when(statisticsService.getAllStats("SAVE_001"))
                .thenReturn(Map.of(StatisticsService.ST_BATTLES_WON, 9L));
        PlayerAchievementEntity unlocked = new PlayerAchievementEntity();
        unlocked.setSaveId("SAVE_001");
        unlocked.setAchievementId("ACH_TEST_STAT");
        when(achievementMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(unlocked));

        achievementService.checkAchievements("SAVE_001");

        verify(achievementMapper, never()).insert(any(PlayerAchievementEntity.class));
    }

    @Test
    void listAchievements_shouldHideUnUnlockedHidden() {
        AchievementsConfig cfg = new AchievementsConfig();
        AchievementsConfig.AchievementConfig normal = new AchievementsConfig.AchievementConfig();
        normal.setId("ACH_NORMAL");
        normal.setName("普通成就");
        normal.setCategory("BATTLE");
        normal.setConditionType("STAT_GE");
        normal.setConditionValue(StatisticsService.ST_BATTLES_WON);
        AchievementsConfig.AchievementConfig hidden = new AchievementsConfig.AchievementConfig();
        hidden.setId("ACH_HIDDEN");
        hidden.setName("隐藏成就");
        hidden.setCategory("SPECIAL");
        hidden.setHidden(true);
        hidden.setConditionType("STAT_GE");
        hidden.setConditionValue(StatisticsService.ST_BATTLES_WON);
        cfg.setAchievements(List.of(normal, hidden));
        configureAchievements(cfg);

        List<Map<String, Object>> list = achievementService.listAchievements("SAVE_001");

        assertEquals(1, list.size());
        assertEquals("ACH_NORMAL", list.get(0).get("id"));
    }

    @Test
    void listAchievements_unlockedHidden_shouldShow() {
        AchievementsConfig cfg = new AchievementsConfig();
        AchievementsConfig.AchievementConfig normal = new AchievementsConfig.AchievementConfig();
        normal.setId("ACH_NORMAL");
        normal.setName("普通成就");
        normal.setCategory("BATTLE");
        normal.setConditionType("STAT_GE");
        normal.setConditionValue(StatisticsService.ST_BATTLES_WON);
        AchievementsConfig.AchievementConfig hidden = new AchievementsConfig.AchievementConfig();
        hidden.setId("ACH_HIDDEN");
        hidden.setName("隐藏成就");
        hidden.setCategory("SPECIAL");
        hidden.setHidden(true);
        hidden.setConditionType("STAT_GE");
        hidden.setConditionValue(StatisticsService.ST_BATTLES_WON);
        cfg.setAchievements(List.of(normal, hidden));
        configureAchievements(cfg);

        PlayerAchievementEntity unlocked = new PlayerAchievementEntity();
        unlocked.setSaveId("SAVE_001");
        unlocked.setAchievementId("ACH_HIDDEN");
        when(achievementMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(unlocked));

        List<Map<String, Object>> list = achievementService.listAchievements("SAVE_001");

        assertEquals(2, list.size());
    }
}