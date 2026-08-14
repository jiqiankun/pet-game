package com.petgame.boss;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.petgame.achievement.service.AchievementService;
import com.petgame.battle.engine.BattleContext;
import com.petgame.battle.event.BattleEvent;
import com.petgame.battle.event.BattleEventType;
import com.petgame.battle.model.BattleSide;
import com.petgame.battle.model.BattleUnit;
import com.petgame.boss.entity.PlayerBossChallengeEntity;
import com.petgame.boss.mapper.PlayerBossChallengeMapper;
import com.petgame.boss.service.BossChallengeService;
import com.petgame.config.GameConfigRegistry;
import com.petgame.config.model.BossChallengesConfig;
import com.petgame.inventory.mapper.PlayerInventoryMapper;
import com.petgame.player.entity.PlayerEntity;
import com.petgame.player.mapper.PlayerMapper;
import com.petgame.statistics.service.StatisticsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Boss 挑战目标服务单元测试（阶段 11，规划决策四）。
 * <p>
 * 验证：仅玩家获胜时判定、四种目标类型判定、奖励发放与统计联动。
 */
class BossChallengeServiceTest {

    private GameConfigRegistry registry;
    private PlayerBossChallengeMapper challengeMapper;
    private PlayerMapper playerMapper;
    private PlayerInventoryMapper inventoryMapper;
    private StatisticsService statisticsService;
    private AchievementService achievementService;
    private BossChallengeService bossChallengeService;

    private BossChallengesConfig.BossChallengeGroup group;

    @BeforeEach
    void setUp() {
        registry = mock(GameConfigRegistry.class);
        challengeMapper = mock(PlayerBossChallengeMapper.class);
        playerMapper = mock(PlayerMapper.class);
        inventoryMapper = mock(PlayerInventoryMapper.class);
        statisticsService = mock(StatisticsService.class);
        achievementService = mock(AchievementService.class);

        group = new BossChallengesConfig.BossChallengeGroup();
        group.setBossId("BOSS_MEADOW_GUARDIAN");
        group.setCompletionTitleId("TITLE_MEADOW_MASTER");
        when(registry.getBossChallengeGroup("BOSS_MEADOW_GUARDIAN")).thenReturn(group);
        when(challengeMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        PlayerEntity player = new PlayerEntity();
        player.setSaveId("SAVE_001");
        player.setGold(100);
        player.setExpPool(0);
        when(playerMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(player);

        bossChallengeService = new BossChallengeService(registry, challengeMapper, playerMapper,
                inventoryMapper, statisticsService, achievementService);
    }

    private BattleContext bossContext(int round, boolean playerWon, boolean playerPetDefeated) {
        BattleContext ctx = new BattleContext("BATTLE_1", 12345L);
        ctx.setBattleType("BOSS");
        ctx.setBossId("BOSS_MEADOW_GUARDIAN");
        ctx.setCurrentRound(round);
        ctx.setPlayerSide(playerSide());
        ctx.setEnemySide(new BattleSide("ENEMY"));
        if (playerPetDefeated) {
            ctx.emit(BattleEvent.of(BattleEventType.PET_DEFEATED, round).target("P_100"));
        }
        ctx.setWinner(playerWon ? "PLAYER" : "ENEMY");
        return ctx;
    }

    private BattleSide playerSide() {
        BattleSide side = new BattleSide("PLAYER");
        side.getUnits().add(unit("P_1", "SPEC_A", "FIRE"));
        side.getUnits().add(unit("P_2", "SPEC_B", "WATER"));
        side.getUnits().add(unit("P_3", "SPEC_C", "GRASS"));
        return side;
    }

    private BattleUnit unit(String id, String species, String element) {
        BattleUnit u = new BattleUnit();
        u.setUnitId(id);
        u.setSpeciesId(species);
        u.setElement(element);
        return u;
    }

    private BossChallengesConfig.ChallengeConfig challenge(String id, String type, int value) {
        BossChallengesConfig.ChallengeConfig ch = new BossChallengesConfig.ChallengeConfig();
        ch.setChallengeId(id);
        ch.setType(type);
        ch.setValue(value);
        return ch;
    }

    @Test
    void recordBossBattle_playerLost_shouldNotJudge() {
        group.setChallenges(List.of(challenge("C_TURN", "TURN_LIMIT", 5)));

        bossChallengeService.recordBossBattle("SAVE_001", bossContext(3, false, false), false);

        verify(challengeMapper, never()).selectList(any(LambdaQueryWrapper.class));
        verify(challengeMapper, never()).insert(any(PlayerBossChallengeEntity.class));
    }

    @Test
    void recordBossBattle_turnLimitMet_shouldComplete() {
        group.setChallenges(List.of(challenge("C_TURN", "TURN_LIMIT", 5)));

        bossChallengeService.recordBossBattle("SAVE_001", bossContext(4, true, false), true);

        verify(challengeMapper).insert(org.mockito.ArgumentMatchers.<PlayerBossChallengeEntity>argThat(e ->
                "SAVE_001".equals(e.getSaveId())
                        && "BOSS_MEADOW_GUARDIAN".equals(e.getBossId())
                        && "C_TURN".equals(e.getChallengeId())));
        verify(statisticsService).increment("SAVE_001", StatisticsService.ST_BOSS_CHALLENGES, 1);
        verify(achievementService).checkAchievements("SAVE_001");
    }

    @Test
    void recordBossBattle_turnLimitNotMet_shouldNotComplete() {
        group.setChallenges(List.of(challenge("C_TURN", "TURN_LIMIT", 5)));

        bossChallengeService.recordBossBattle("SAVE_001", bossContext(6, true, false), true);

        verify(challengeMapper, never()).insert(any(PlayerBossChallengeEntity.class));
    }

    @Test
    void recordBossBattle_noRecoveryItemUsed_shouldComplete() {
        group.setChallenges(List.of(challenge("C_NO_ITEM", "NO_RECOVERY_ITEM", 1)));

        BattleContext ctx = bossContext(3, true, false);
        ctx.getConsumedRecoveryItems().clear();
        bossChallengeService.recordBossBattle("SAVE_001", ctx, true);

        verify(challengeMapper).insert(org.mockito.ArgumentMatchers.<PlayerBossChallengeEntity>argThat(e -> "C_NO_ITEM".equals(e.getChallengeId())));
    }

    @Test
    void recordBossBattle_recoveryItemUsed_shouldNotComplete() {
        group.setChallenges(List.of(challenge("C_NO_ITEM", "NO_RECOVERY_ITEM", 1)));

        BattleContext ctx = bossContext(3, true, false);
        ctx.getConsumedRecoveryItems().put("ITEM_POTION_SMALL", 1);
        bossChallengeService.recordBossBattle("SAVE_001", ctx, true);

        verify(challengeMapper, never()).insert(any(PlayerBossChallengeEntity.class));
    }

    @Test
    void recordBossBattle_noPetFainted_shouldComplete() {
        group.setChallenges(List.of(challenge("C_NO_FAINT", "NO_PET_FAINTED", 1)));

        bossChallengeService.recordBossBattle("SAVE_001", bossContext(3, true, false), true);

        verify(challengeMapper).insert(org.mockito.ArgumentMatchers.<PlayerBossChallengeEntity>argThat(e -> "C_NO_FAINT".equals(e.getChallengeId())));
    }

    @Test
    void recordBossBattle_petDefeated_shouldNotComplete() {
        group.setChallenges(List.of(challenge("C_NO_FAINT", "NO_PET_FAINTED", 1)));

        bossChallengeService.recordBossBattle("SAVE_001", bossContext(3, true, true), true);

        verify(challengeMapper, never()).insert(any(PlayerBossChallengeEntity.class));
    }

    @Test
    void recordBossBattle_multiElementThree_shouldComplete() {
        group.setChallenges(List.of(challenge("C_MULTI", "MULTI_ELEMENT", 3)));

        bossChallengeService.recordBossBattle("SAVE_001", bossContext(3, true, false), true);

        verify(challengeMapper).insert(org.mockito.ArgumentMatchers.<PlayerBossChallengeEntity>argThat(e -> "C_MULTI".equals(e.getChallengeId())));
    }

    @Test
    void recordBossBattle_multiElementTwo_shouldNotComplete() {
        group.setChallenges(List.of(challenge("C_MULTI", "MULTI_ELEMENT", 3)));

        BattleContext ctx = bossContext(3, true, false);
        ctx.getPlayerSide().getUnits().clear();
        ctx.getPlayerSide().getUnits().add(unit("P_1", "SPEC_A", "FIRE"));
        ctx.getPlayerSide().getUnits().add(unit("P_2", "SPEC_B", "FIRE"));
        bossChallengeService.recordBossBattle("SAVE_001", ctx, true);

        verify(challengeMapper, never()).insert(any(PlayerBossChallengeEntity.class));
    }

    @Test
    void listChallenges_completionTitle_shouldReflectAllCompleted() {
        BossChallengesConfig.ChallengeConfig c1 = challenge("C_TURN", "TURN_LIMIT", 5);
        BossChallengesConfig.ChallengeConfig c2 = challenge("C_NO_ITEM", "NO_RECOVERY_ITEM", 1);
        group.setChallenges(List.of(c1, c2));

        PlayerBossChallengeEntity done = new PlayerBossChallengeEntity();
        done.setSaveId("SAVE_001");
        done.setBossId("BOSS_MEADOW_GUARDIAN");
        done.setChallengeId("C_TURN");
        when(challengeMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(done));

        BossChallengesConfig cfg = new BossChallengesConfig();
        cfg.setGroups(List.of(group));
        when(registry.getBossChallengesConfig()).thenReturn(cfg);

        List<java.util.Map<String, Object>> list = bossChallengeService.listChallenges("SAVE_001");

        assertEquals(1, list.size());
        assertEquals("BOSS_MEADOW_GUARDIAN", list.get(0).get("bossId"));
        assertEquals("TITLE_MEADOW_MASTER", list.get(0).get("completionTitleId"));
        assertFalse((Boolean) list.get(0).get("allCompleted"));
        @SuppressWarnings("unchecked")
        List<java.util.Map<String, Object>> challs = (List<java.util.Map<String, Object>>) list.get(0).get("challenges");
        assertEquals(2, challs.size());
        assertTrue((Boolean) challs.get(0).get("completed"));
        assertFalse((Boolean) challs.get(1).get("completed"));
    }
}