package com.petgame.battle.victory;

import com.petgame.battle.engine.BattleContext;
import com.petgame.battle.event.BattleEvent;
import com.petgame.battle.event.BattleEventType;
import com.petgame.battle.model.BattleSide;
import com.petgame.battle.model.BattleUnit;
import com.petgame.config.GameConfigRegistry;
import com.petgame.config.model.BossesConfig;
import com.petgame.config.model.PetSpeciesConfig;
import com.petgame.config.model.VictoryInteractionConfig;
import com.petgame.statistics.service.StatisticsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 敌方胜利互动服务单元测试（阶段 12，需求 §152）。
 * <p>
 * 验证：获胜方类型映射、战况标签判定、候选选择（专属/公共池/回退）、
 * 权重随机 + 防重复、无匹配返回 null（不阻断主流程）。
 */
class VictoryInteractionServiceTest {

    private GameConfigRegistry registry;
    private StatisticsService statisticsService;
    private VictoryInteractionService service;

    @BeforeEach
    void setUp() {
        registry = mock(GameConfigRegistry.class);
        statisticsService = mock(StatisticsService.class);
        service = new VictoryInteractionService(registry, statisticsService);
    }

    // ==================== 获胜方类型映射 ====================

    @Test
    void select_wildBattle_shouldResolveWildPetWinnerType() {
        VictoryInteractionConfig config = config(interaction("VI_WILD",
                "WILD_PET", null, List.of("NORMAL_LOSS"), "DIALOGUE", 100, "COMMON", "啊呜"));
        when(registry.getVictoryInteractionConfig()).thenReturn(config);

        BattleContext ctx = wildCtx(60, 1, List.of());
        VictoryInteractionView view = service.select(ctx, "SAVE_1");

        assertNotNull(view);
        assertEquals("VI_WILD", view.getId());
        assertEquals("WILD_PET", view.getWinnerType());
    }

    @Test
    void select_bossBattle_shouldResolveBossWinnerType() {
        VictoryInteractionConfig config = config(interaction("VI_BOSS",
                "BOSS", null, List.of("NORMAL_LOSS"), "DIALOGUE", 100, "COMMON", "哼"));
        when(registry.getVictoryInteractionConfig()).thenReturn(config);
        when(registry.getBoss("BOSS_1")).thenReturn(null);

        BattleContext ctx = bossCtx("BOSS_1", 60, List.of());
        VictoryInteractionView view = service.select(ctx, "SAVE_1");

        assertNotNull(view);
        assertEquals("BOSS", view.getWinnerType());
    }

    @Test
    void select_testBattle_shouldResolveTrainerWinnerType() {
        VictoryInteractionConfig config = config(interaction("VI_TR",
                "TRAINER", null, List.of("NORMAL_LOSS"), "DIALOGUE", 100, "COMMON", "回去练练"));
        when(registry.getVictoryInteractionConfig()).thenReturn(config);

        BattleContext ctx = testCtx(60, 1, List.of());
        VictoryInteractionView view = service.select(ctx, "SAVE_1");

        assertNotNull(view);
        assertEquals("TRAINER", view.getWinnerType());
    }

    // ==================== 战况标签 ====================

    @Test
    void select_closeLoss_whenEnemyHpLow() {
        VictoryInteractionConfig config = new VictoryInteractionConfig();
        config.setInteractions(List.of(
                interaction("VI_CLOSE", "WILD_PET", null, List.of("CLOSE_LOSS"), "DIALOGUE", 100, "COMMON", "惜败"),
                otherWinning("VI_NORMAL")));
        when(registry.getVictoryInteractionConfig()).thenReturn(config);

        // 敌方存活 HP = 10%，命中 CLOSE_LOSS
        BattleContext ctx = wildCtx(10, 1, List.of());
        VictoryInteractionView view = service.select(ctx, "SAVE_1");

        assertNotNull(view);
        assertEquals("VI_CLOSE", view.getId());
        assertEquals("CLOSE_LOSS", view.getContext());
    }

    @Test
    void select_crushed_whenEnemyHpHigh() {
        VictoryInteractionConfig config = new VictoryInteractionConfig();
        config.setInteractions(List.of(
                interaction("VI_CRUSH", "WILD_PET", null, List.of("CRUSHED"), "DIALOGUE", 100, "COMMON", "碾压"),
                otherWinning("VI_NORMAL")));
        when(registry.getVictoryInteractionConfig()).thenReturn(config);

        BattleContext ctx = wildCtx(95, 1, List.of());
        VictoryInteractionView view = service.select(ctx, "SAVE_1");

        assertNotNull(view);
        assertEquals("VI_CRUSH", view.getId());
    }

    @Test
    void select_normal_whenNoSpecialTag() {
        VictoryInteractionConfig config = config(interaction("VI_N",
                "WILD_PET", null, List.of("NORMAL_LOSS"), "DIALOGUE", 100, "COMMON", "普通"));
        when(registry.getVictoryInteractionConfig()).thenReturn(config);

        BattleContext ctx = wildCtx(60, 1, List.of());
        VictoryInteractionView view = service.select(ctx, "SAVE_1");

        assertNotNull(view);
        assertEquals("VI_N", view.getId());
        assertEquals("NORMAL_LOSS", view.getContext());
    }

    @Test
    void select_comeback_whenPlayerKnockedOutEnemy() {
        VictoryInteractionConfig config = config(interaction("VI_CB",
                "WILD_PET", null, List.of("COMEBACK_LOSS"), "DIALOGUE", 100, "COMMON", "反杀"));
        when(registry.getVictoryInteractionConfig()).thenReturn(config);

        // 玩家曾击败一个敌方单位
        BattleContext ctx = wildCtx(60, 1, List.of(
                BattleEvent.of(BattleEventType.PET_DEFEATED, 3).target("E_1")));
        VictoryInteractionView view = service.select(ctx, "SAVE_1");

        assertNotNull(view);
        assertEquals("VI_CB", view.getId());
    }

    // ==================== 专属 vs 公共池 ====================

    @Test
    void select_specificTargetId_preferredOverCommonPool() {
        VictoryInteractionConfig config = new VictoryInteractionConfig();
        config.setInteractions(List.of(
                interaction("VI_BOSS_SPEC", "BOSS", "BOSS_1", List.of(), "DIALOGUE", 100, "COMMON", "专属"),
                interaction("VI_BOSS_COMMON", "BOSS", null, List.of(), "DIALOGUE", 100, "COMMON", "公共")));
        when(registry.getVictoryInteractionConfig()).thenReturn(config);
        when(registry.getBoss("BOSS_1")).thenReturn(null);

        BattleContext ctx = bossCtx("BOSS_1", 60, List.of());
        VictoryInteractionView view = service.select(ctx, "SAVE_1");

        assertNotNull(view);
        assertEquals("VI_BOSS_SPEC", view.getId());
    }

    @Test
    void select_noMatch_returnNull() {
        when(registry.getVictoryInteractionConfig()).thenReturn(null);

        BattleContext ctx = wildCtx(60, 1, List.of());
        VictoryInteractionView view = service.select(ctx, "SAVE_1");

        assertNull(view);
    }

    // ==================== 防重复 ====================

    @Test
    void select_antiRepeat_shouldAvoidImmediateRepeat() throws Exception {
        VictoryInteractionConfig config = new VictoryInteractionConfig();
        config.setInteractions(List.of(
                interaction("VI_A", "WILD_PET", null, List.of(), "DIALOGUE", 1, "COMMON", "A"),
                interaction("VI_B", "WILD_PET", null, List.of(), "DIALOGUE", 1, "COMMON", "B")));
        when(registry.getVictoryInteractionConfig()).thenReturn(config);

        BattleContext ctx = wildCtx(60, 1, List.of());
        // 多次选择，因防重复队列较短，两次不会拿到相同 ID（队列在两次选择间已加入）
        String first = service.select(ctx, "SAVE_1").getId();
        String second = service.select(ctx, "SAVE_1").getId();
        assertNotEquals(first, second);
    }

    // ==================== Boss 挑战记录 ====================

    @Test
    void recordBossChallenge_wildBattle_shouldNotRecord() {
        BattleContext ctx = wildCtx(60, 1, List.of());
        service.recordBossChallenge("SAVE_1", ctx, false);
        verify(statisticsService, never()).increment(anyString(), anyString(), anyLong());
    }

    @Test
    void recordBossChallenge_bossWon_shouldResetConsecutive() {
        BattleContext ctx = bossCtx("BOSS_1", 60, List.of());
        service.recordBossChallenge("SAVE_1", ctx, true);
        verify(statisticsService).set("SAVE_1",
                VictoryInteractionService.PREFIX_BOSS_CONSECUTIVE + "BOSS_1", 0L);
    }

    @Test
    void recordBossChallenge_bossLost_shouldIncrementConsecutive() {
        BattleContext ctx = bossCtx("BOSS_1", 60, List.of());
        service.recordBossChallenge("SAVE_1", ctx, false);
        verify(statisticsService).increment("SAVE_1",
                VictoryInteractionService.PREFIX_BOSS_CHALLENGE + "BOSS_1", 1L);
        verify(statisticsService).increment("SAVE_1",
                VictoryInteractionService.PREFIX_BOSS_CONSECUTIVE + "BOSS_1", 1L);
    }

    // ==================== 工具方法 ====================

    private VictoryInteractionConfig config(VictoryInteractionConfig.Interaction... items) {
        VictoryInteractionConfig config = new VictoryInteractionConfig();
        config.setInteractions(List.of(items));
        return config;
    }

    private VictoryInteractionConfig.Interaction interaction(String id, String winnerType,
                                                             String targetId, List<String> contexts,
                                                             String presentationType, int weight,
                                                             String rarity, String text) {
        VictoryInteractionConfig.Interaction it = new VictoryInteractionConfig.Interaction();
        it.setId(id);
        it.setWinnerType(winnerType);
        it.setTargetId(targetId);
        it.setContexts(contexts);
        it.setPresentationType(presentationType);
        it.setWeight(weight);
        it.setRarity(rarity);
        it.setText(text);
        return it;
    }

    /** 构造一个必然不匹配当前战况的对照互动。 */
    private VictoryInteractionConfig.Interaction otherWinning(String id) {
        return interaction(id, "TRAINER", null, List.of("NORMAL_LOSS"), "DIALOGUE", 100, "COMMON", "其他");
    }

    /** 构造一场已结束的野外战斗（敌方单位 E_1，与玩家同侧单位 P_1）。 */
    private BattleContext wildCtx(int enemyHp, int round, List<BattleEvent> extraEvents) {
        BattleContext ctx = new BattleContext("BATTLE", 1L);
        ctx.setBattleType("WILD");
        ctx.setFinished(true);
        ctx.setWinner("ENEMY");
        ctx.setCurrentRound(round);

        BattleUnit player = unit("P_1", null, 100, 100, true);
        ctx.setPlayerSide(side(player));

        BattleUnit enemy = unit("E_1", "SPEC_WILD", enemyHp, 100, true);
        BattleSide enemySide = new BattleSide("ENEMY");
        enemySide.getUnits().add(enemy);
        ctx.setEnemySide(enemySide);

        for (BattleEvent e : extraEvents) {
            ctx.emit(e);
        }
        return ctx;
    }

    private BattleContext bossCtx(String bossId, int enemyHp, List<BattleEvent> extraEvents) {
        BattleContext ctx = new BattleContext("BATTLE", 1L);
        ctx.setBattleType("BOSS");
        ctx.setBossId(bossId);
        ctx.setFinished(true);
        ctx.setWinner("ENEMY");
        ctx.setCurrentRound(1);

        BattleUnit player = unit("P_1", null, 100, 100, true);
        ctx.setPlayerSide(side(player));

        BattleUnit enemy = unit("E_1", null, enemyHp, 100, true);
        BattleSide enemySide = new BattleSide("ENEMY");
        enemySide.getUnits().add(enemy);
        ctx.setEnemySide(enemySide);

        for (BattleEvent e : extraEvents) {
            ctx.emit(e);
        }
        return ctx;
    }

    private BattleContext testCtx(int enemyHp, int round, List<BattleEvent> extraEvents) {
        BattleContext ctx = wildCtx(enemyHp, round, extraEvents);
        ctx.setBattleType("TEST");
        return ctx;
    }

    private BattleSide side(BattleUnit... units) {
        BattleSide side = new BattleSide("PLAYER");
        for (BattleUnit u : units) {
            side.getUnits().add(u);
        }
        return side;
    }

    private BattleUnit unit(String unitId, String speciesId, int currentHp, int maxHp, boolean active) {
        BattleUnit unit = new BattleUnit();
        unit.setUnitId(unitId);
        unit.setSpeciesId(speciesId);
        unit.setName(unitId);
        unit.setMaxHp(maxHp);
        unit.setCurrentHp(currentHp);
        unit.setAlive(currentHp > 0);
        unit.setActive(active);
        return unit;
    }
}