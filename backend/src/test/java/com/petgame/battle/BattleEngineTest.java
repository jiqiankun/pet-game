package com.petgame.battle;

import com.petgame.battle.ai.DecisionProvider;
import com.petgame.battle.engine.BattleContext;
import com.petgame.battle.engine.BattleEngine;
import com.petgame.battle.engine.TurnResult;
import com.petgame.battle.event.BattleEvent;
import com.petgame.battle.event.BattleEventType;
import com.petgame.battle.model.BattleAction;
import com.petgame.battle.model.BattleUnit;
import com.petgame.battle.model.StatusInstance;
import com.petgame.common.BusinessException;
import com.petgame.config.GameConfigRegistry;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.petgame.battle.BattleTestFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * BattleEngine 核心规则单元测试（阶段 3 验收标准）。
 * <p>
 * 覆盖：伤害结算链路（克制 ×1.50 / 被克 ×0.75 / 本属性 ×1.20 / 防御减伤 / 最低 1 点）、
 * 暴击（1.4~2.0，治疗不暴击）、行动顺序（速度排序、每回合重算、固定种子复现）、
 * 冷却、蓄力、群体、换宠、补位、防御姿态、沉默、DOT、嘲讽、不屈被动、胜负判定。
 */
class BattleEngineTest {

    /** 敌方全体空操作（不影响玩家结算的确定性测试）。 */
    private final DecisionProvider waitProvider = (ctx, side) ->
            side.getActiveAliveUnits().stream()
                    .map(u -> BattleAction.skill(u.getUnitId(), "SKILL_WAIT", null))
                    .toList();

    private BattleEngine engine(GameConfigRegistry registry) {
        return new BattleEngine(registry, waitProvider);
    }

    private BattleEngine engine(GameConfigRegistry registry, DecisionProvider enemyProvider) {
        return new BattleEngine(registry, enemyProvider);
    }

    // ---- 伤害结算链路 ----

    @Test
    void damageChain_advantageAndSameElement_shouldApply150And120() {
        GameConfigRegistry registry = buildRegistry(0);
        BattleEngine engine = engine(registry);
        BattleUnit player = active(unit("P1", "WATER", 200, 50, 50, 50, 50, 100, "SKILL_HIT"), 0);
        BattleUnit enemy = active(unit("E1", "FIRE", 200, 40, 40, 50, 50, 10, "SKILL_WAIT"), 0);
        BattleContext ctx = context(1L, List.of(player), List.of(enemy));

        TurnResult result = engine.playTurn(ctx, List.of(BattleAction.skill("P1", "SKILL_HIT", "E1")));

        BattleEvent damage = findEvent(result.getEvents(), BattleEventType.DAMAGE);
        assertNotNull(damage, "应产生伤害事件");
        // 基础 10 + 力量 50 = 60；防御减伤 60×200/250 = 48；克制 ×1.50；本属性 ×1.20 → 86.4 → 86
        assertEquals(86, damage.getValue());
        assertEquals("ADVANTAGE", damage.getElementRelation());
        assertEquals(1.5, (double) damage.getData().get("elementMultiplier"), 0.001);
        assertEquals(1.2, (double) damage.getData().get("sameElementMultiplier"), 0.001);
        assertEquals(200 - 86, enemy.getCurrentHp());
    }

    @Test
    void damageChain_disadvantage_shouldApply075() {
        GameConfigRegistry registry = buildRegistry(0);
        BattleEngine engine = engine(registry);
        BattleUnit player = active(unit("P1", "WATER", 200, 50, 50, 50, 50, 100, "SKILL_FIRE_HIT"), 0);
        BattleUnit enemy = active(unit("E1", "WATER", 200, 40, 40, 50, 50, 10, "SKILL_WAIT"), 0);
        BattleContext ctx = context(2L, List.of(player), List.of(enemy));

        TurnResult result = engine.playTurn(ctx, List.of(BattleAction.skill("P1", "SKILL_FIRE_HIT", "E1")));

        BattleEvent damage = findEvent(result.getEvents(), BattleEventType.DAMAGE);
        assertNotNull(damage);
        // 48 × 0.75 = 36；施法者水 ≠ 技能火，无本属性加成
        assertEquals(36, damage.getValue());
        assertEquals("DISADVANTAGE", damage.getElementRelation());
    }

    @Test
    void damageChain_zeroBase_shouldDealMinimumOneDamage() {
        GameConfigRegistry registry = buildRegistry(0);
        BattleEngine engine = engine(registry);
        BattleUnit player = active(unit("P1", "WATER", 200, 50, 50, 50, 50, 100, "SKILL_WEAK"), 0);
        BattleUnit enemy = active(unit("E1", "FIRE", 200, 40, 40, 999, 999, 10, "SKILL_WAIT"), 0);
        BattleContext ctx = context(3L, List.of(player), List.of(enemy));

        TurnResult result = engine.playTurn(ctx, List.of(BattleAction.skill("P1", "SKILL_WEAK", "E1")));

        BattleEvent damage = findEvent(result.getEvents(), BattleEventType.DAMAGE);
        assertNotNull(damage);
        assertEquals(1, damage.getValue(), "正常命中最低 1 点伤害");
    }

    @Test
    void critical_alwaysCrit_shouldStayWithin140To200() {
        GameConfigRegistry registry = buildRegistry(1.0);
        BattleEngine engine = engine(registry);
        BattleUnit player = active(unit("P1", "WATER", 200, 50, 50, 50, 50, 100, "SKILL_HIT"), 0);
        BattleUnit enemy = active(unit("E1", "FIRE", 500, 40, 40, 50, 50, 10, "SKILL_WAIT"), 0);
        BattleContext ctx = context(4L, List.of(player), List.of(enemy));

        TurnResult result = engine.playTurn(ctx, List.of(BattleAction.skill("P1", "SKILL_HIT", "E1")));

        BattleEvent damage = findEvent(result.getEvents(), BattleEventType.DAMAGE);
        assertNotNull(damage);
        assertTrue(damage.getCritical(), "100% 暴击率下应暴击");
        double critMultiplier = (double) damage.getData().get("critMultiplier");
        assertTrue(critMultiplier >= 1.4 && critMultiplier <= 2.0,
                "暴击倍率应在 [1.4, 2.0]，实际: " + critMultiplier);
        // 86.4 × [1.4, 2.0] → [121, 173]
        assertTrue(damage.getValue() >= 121 && damage.getValue() <= 173);
    }

    @Test
    void heal_sameElementBonus_andNeverCrits() {
        GameConfigRegistry registry = buildRegistry(1.0);
        BattleEngine engine = engine(registry);
        BattleUnit player = active(unit("P1", "WATER", 200, 50, 50, 50, 50, 100, "SKILL_HEAL"), 0);
        player.setCurrentHp(50);
        BattleUnit enemy = active(unit("E1", "FIRE", 200, 40, 40, 50, 50, 10, "SKILL_WAIT"), 0);
        BattleContext ctx = context(5L, List.of(player), List.of(enemy));

        TurnResult result = engine.playTurn(ctx, List.of(BattleAction.skill("P1", "SKILL_HEAL", null)));

        BattleEvent heal = findEvent(result.getEvents(), BattleEventType.HEAL);
        assertNotNull(heal, "应产生治疗事件");
        // (10 + 灵力 50) × 本属性 1.2 = 72；治疗不暴击
        assertEquals(72, heal.getValue());
        assertEquals(122, player.getCurrentHp());
        assertTrue(result.getEvents().stream().noneMatch(e -> e.getType() == BattleEventType.CRITICAL),
                "治疗不应产生暴击");
    }

    // ---- 行动顺序 ----

    @Test
    void actionOrder_shouldSortBySpeedDescending() {
        GameConfigRegistry registry = buildRegistry(0);
        BattleEngine engine = engine(registry);
        BattleContext ctx = context(6L,
                List.of(active(unit("P1", "WATER", 100, 10, 10, 10, 10, 90, "SKILL_WAIT"), 0),
                        active(unit("P2", "WATER", 100, 10, 10, 10, 10, 10, "SKILL_WAIT"), 1)),
                List.of(active(unit("E1", "FIRE", 100, 10, 10, 10, 10, 50, "SKILL_WAIT"), 0)));

        TurnResult result = engine.playTurn(ctx, List.of(
                BattleAction.skill("P1", "SKILL_WAIT", null),
                BattleAction.skill("P2", "SKILL_WAIT", null)));

        BattleEvent orderEvent = findEvent(result.getEvents(), BattleEventType.ACTION_ORDER);
        assertNotNull(orderEvent);
        assertEquals(List.of("P1", "E1", "P2"), orderEvent.getData().get("order"));
    }

    @Test
    void fixedSeed_shouldReproduceIdenticalBattle() {
        List<String> run1 = runReproducibleScenario(424242L);
        List<String> run2 = runReproducibleScenario(424242L);
        assertFalse(run1.isEmpty());
        assertEquals(run1, run2, "相同种子的完整战斗事件序列必须完全一致");
    }

    private List<String> runReproducibleScenario(long seed) {
        GameConfigRegistry registry = buildRegistry(0.3);
        BattleEngine engine = engine(registry);
        BattleContext ctx = context(seed,
                List.of(active(unit("P1", "WATER", 500, 50, 50, 50, 50, 90, "SKILL_HIT"), 0)),
                List.of(active(unit("E1", "FIRE", 60, 40, 40, 50, 50, 30, "SKILL_WAIT"), 0),
                        active(unit("E2", "METAL", 60, 40, 40, 50, 50, 20, "SKILL_WAIT"), 1),
                        active(unit("E3", "WATER", 60, 40, 40, 50, 50, 10, "SKILL_WAIT"), 2)));
        engine.startBattle(ctx);

        int guard = 0;
        while (!ctx.isFinished() && guard++ < 50) {
            List<BattleUnit> targets = ctx.getEnemySide().getActiveAliveUnits();
            engine.playTurn(ctx, List.of(BattleAction.skill("P1", "SKILL_HIT", targets.get(0).getUnitId())));
        }
        assertTrue(ctx.isFinished(), "场景应在 50 回合内结束");
        assertEquals("PLAYER", ctx.getWinner());

        List<String> serialized = new ArrayList<>();
        for (BattleEvent event : ctx.getEvents()) {
            serialized.add(event.getType() + "|" + event.getSourceId() + "|" + event.getTargetId()
                    + "|" + event.getValue() + "|" + event.getCritical());
        }
        return serialized;
    }

    // ---- 冷却 / 蓄力 / 群体 ----

    @Test
    void cooldown_shouldBlockReuseAndDecrementEachRound() {
        GameConfigRegistry registry = buildRegistry(0);
        BattleEngine engine = engine(registry);
        BattleUnit player = active(unit("P1", "WATER", 200, 50, 50, 50, 50, 100, "SKILL_CD"), 0);
        BattleUnit enemy = active(unit("E1", "FIRE", 500, 40, 40, 50, 50, 10, "SKILL_WAIT"), 0);
        BattleContext ctx = context(7L, List.of(player), List.of(enemy));

        engine.playTurn(ctx, List.of(BattleAction.skill("P1", "SKILL_CD", "E1")));
        assertEquals(1, player.getCooldowns().get("SKILL_CD"), "冷却 2 回合，回合结束递减后剩 1");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> engine.playTurn(ctx, List.of(BattleAction.skill("P1", "SKILL_CD", "E1"))));
        assertEquals("SKILL_ON_COOLDOWN", ex.getErrorCode());

        engine.playTurn(ctx, List.of(BattleAction.defend("P1")));
        assertEquals(0, player.getCooldowns().get("SKILL_CD"), "再经一回合应冷却完毕");

        assertDoesNotThrow(() -> engine.playTurn(ctx, List.of(BattleAction.skill("P1", "SKILL_CD", "E1"))));
    }

    @Test
    void chargeSkill_shouldChargeFirstRoundAndReleaseNext() {
        GameConfigRegistry registry = buildRegistry(0);
        BattleEngine engine = engine(registry);
        BattleUnit player = active(unit("P1", "WATER", 200, 50, 50, 50, 50, 100, "SKILL_CHARGE"), 0);
        BattleUnit enemy = active(unit("E1", "FIRE", 500, 40, 40, 50, 50, 10, "SKILL_WAIT"), 0);
        BattleContext ctx = context(8L, List.of(player), List.of(enemy));

        TurnResult round1 = engine.playTurn(ctx, List.of(BattleAction.skill("P1", "SKILL_CHARGE", "E1")));
        assertNotNull(findEvent(round1.getEvents(), BattleEventType.CHARGING), "第一回合应进入蓄力");
        assertTrue(round1.getEvents().stream().noneMatch(e -> e.getType() == BattleEventType.DAMAGE),
                "蓄力回合不应造成伤害");

        TurnResult round2 = engine.playTurn(ctx, List.of(BattleAction.defend("P1")));
        assertNotNull(findEvent(round2.getEvents(), BattleEventType.SKILL_CAST), "第二回合应释放蓄力技能");
        assertNotNull(findEvent(round2.getEvents(), BattleEventType.DAMAGE));
        assertNull(player.getChargingSkillId(), "释放后蓄力状态应清除");
    }

    @Test
    void aoeSkill_shouldHitAllActiveEnemies() {
        GameConfigRegistry registry = buildRegistry(0);
        BattleEngine engine = engine(registry);
        BattleContext ctx = context(9L,
                List.of(active(unit("P1", "WATER", 200, 50, 50, 50, 50, 100, "SKILL_AOE"), 0)),
                List.of(active(unit("E1", "FIRE", 500, 40, 40, 50, 50, 10, "SKILL_WAIT"), 0),
                        active(unit("E2", "METAL", 500, 40, 40, 50, 50, 10, "SKILL_WAIT"), 1),
                        active(unit("E3", "WATER", 500, 40, 40, 50, 50, 10, "SKILL_WAIT"), 2)));

        TurnResult result = engine.playTurn(ctx, List.of(BattleAction.skill("P1", "SKILL_AOE", null)));

        long damageCount = result.getEvents().stream()
                .filter(e -> e.getType() == BattleEventType.DAMAGE && "P1".equals(e.getSourceId()))
                .count();
        assertEquals(3, damageCount, "群体技能应命中全部 3 个存活上场敌方单位");
    }

    // ---- 换宠 / 补位 ----

    @Test
    void switch_shouldConsumeActionAndInheritPosition() {
        GameConfigRegistry registry = buildRegistry(0);
        BattleEngine engine = engine(registry);
        BattleUnit outgoing = active(unit("P1", "WATER", 200, 50, 50, 50, 50, 100, "SKILL_WAIT"), 0);
        BattleUnit incoming = unit("P2", "FIRE", 200, 50, 50, 50, 50, 50, "SKILL_WAIT");
        BattleUnit enemy = active(unit("E1", "FIRE", 200, 40, 40, 50, 50, 10, "SKILL_WAIT"), 0);
        BattleContext ctx = context(10L, List.of(outgoing, incoming), List.of(enemy));

        TurnResult result = engine.playTurn(ctx, List.of(BattleAction.switchPet("P1", "P2")));

        assertNotNull(findEvent(result.getEvents(), BattleEventType.PET_SWITCHED));
        assertFalse(outgoing.isActive(), "下场单位应变为候补");
        assertTrue(incoming.isActive(), "候补单位应上场");
        assertEquals(0, incoming.getPosition(), "新上场单位应继承原位置");
        assertTrue(result.getEvents().stream()
                        .noneMatch(e -> "P2".equals(e.getSourceId())
                                && (e.getType() == BattleEventType.DEFEND || e.getType() == BattleEventType.SKILL_CAST)),
                "中途换上的单位本回合不行动");
    }

    @Test
    void defeat_shouldTriggerBenchReplacementWithoutNextTurnCost() {
        GameConfigRegistry registry = buildRegistry(0);
        DecisionProvider killer = (ctx, side) -> {
            List<BattleUnit> targets = ctx.getPlayerSide().getActiveAliveUnits();
            return side.getActiveAliveUnits().stream()
                    .map(u -> BattleAction.skill(u.getUnitId(), "SKILL_BIG", targets.get(0).getUnitId()))
                    .toList();
        };
        BattleEngine engine = engine(registry, killer);
        BattleUnit front = active(unit("P1", "WATER", 100, 50, 50, 50, 50, 10, "SKILL_WAIT"), 0);
        BattleUnit bench = unit("P2", "FIRE", 200, 50, 50, 50, 50, 50, "SKILL_WAIT");
        BattleUnit enemy = active(unit("E1", "FIRE", 500, 50, 50, 50, 50, 100, "SKILL_BIG"), 0);
        BattleContext ctx = context(11L, List.of(front, bench), List.of(enemy));

        TurnResult result = engine.playTurn(ctx, List.of(BattleAction.defend("P1")));

        assertFalse(front.isAlive(), "前排单位应倒下");
        assertNotNull(findEvent(result.getEvents(), BattleEventType.PET_DEFEATED));
        assertNotNull(findEvent(result.getEvents(), BattleEventType.PET_REPLACED));
        assertTrue(bench.isActive(), "候补应自动补位");
        assertEquals(0, bench.getPosition(), "补位单位应继承倒下单位的位置");
        assertEquals(200, bench.getCurrentHp(), "补位单位本回合不行动且保持满血");
    }

    // ---- 防御 / 沉默 / DOT / 嘲讽 ----

    @Test
    void defend_shouldHalveIncomingDamage() {
        long seed = 12L;
        int damageWhenDefending = runDefendScenario(seed, true);
        int damageWhenIdle = runDefendScenario(seed, false);
        assertEquals(48, damageWhenIdle, "未防御时应受到完整伤害");
        assertEquals(24, damageWhenDefending, "防御姿态应减半伤害");
    }

    private int runDefendScenario(long seed, boolean defend) {
        GameConfigRegistry registry = buildRegistry(0);
        DecisionProvider attacker = (ctx, side) -> side.getActiveAliveUnits().stream()
                .map(u -> BattleAction.skill(u.getUnitId(), "SKILL_HIT", "P1"))
                .toList();
        BattleEngine engine = engine(registry, attacker);
        BattleUnit player = active(unit("P1", "METAL", 200, 50, 50, 50, 50, 100, "SKILL_WAIT"), 0);
        // 敌方为 METAL：水技能对金无克制关系，且不会触发敌方本属性加成
        BattleUnit enemy = active(unit("E1", "METAL", 200, 50, 50, 50, 50, 10, "SKILL_HIT"), 0);
        BattleContext ctx = context(seed, List.of(player), List.of(enemy));

        BattleAction action = defend ? BattleAction.defend("P1") : BattleAction.skill("P1", "SKILL_WAIT", null);
        engine.playTurn(ctx, List.of(action));
        return 200 - player.getCurrentHp();
    }

    @Test
    void silence_shouldForceDefendInsteadOfSkill() {
        GameConfigRegistry registry = buildRegistry(0);
        BattleEngine engine = engine(registry);
        BattleUnit player = active(unit("P1", "WATER", 200, 50, 50, 50, 50, 100, "SKILL_HIT"), 0);
        player.getStatuses().add(new StatusInstance("SILENCE", 3, "E1"));
        BattleUnit enemy = active(unit("E1", "FIRE", 200, 40, 40, 50, 50, 10, "SKILL_WAIT"), 0);
        BattleContext ctx = context(13L, List.of(player), List.of(enemy));

        TurnResult result = engine.playTurn(ctx, List.of(BattleAction.skill("P1", "SKILL_HIT", "E1")));

        BattleEvent defendEvent = findEvent(result.getEvents(), BattleEventType.DEFEND);
        assertNotNull(defendEvent, "沉默时技能行动应被强制转为防御");
        assertEquals("SILENCE", defendEvent.getData().get("reason"));
        assertTrue(result.getEvents().stream()
                        .noneMatch(e -> e.getType() == BattleEventType.SKILL_CAST && "P1".equals(e.getSourceId())),
                "沉默单位不应释放技能");
    }

    @Test
    void dot_shouldTickAtRoundEndBasedOnMaxHp() {
        GameConfigRegistry registry = buildRegistry(0);
        BattleEngine engine = engine(registry);
        BattleUnit player = active(unit("P1", "WATER", 100, 50, 50, 50, 50, 100, "SKILL_WAIT"), 0);
        player.getStatuses().add(new StatusInstance("BURN", 2, "E1"));
        BattleUnit enemy = active(unit("E1", "FIRE", 200, 40, 40, 50, 50, 10, "SKILL_WAIT"), 0);
        BattleContext ctx = context(14L, List.of(player), List.of(enemy));

        TurnResult result = engine.playTurn(ctx, List.of(BattleAction.skill("P1", "SKILL_WAIT", null)));

        BattleEvent tick = findEvent(result.getEvents(), BattleEventType.STATUS_TICK);
        assertNotNull(tick, "回合结束应结算 DOT");
        assertEquals(6, tick.getValue(), "灼烧 = 最大 HP 100 × 6% = 6");
        assertEquals(94, player.getCurrentHp());
        assertEquals(1, player.getStatuses().get(0).getRemainingTurns(), "DOT 持续时间应递减");
    }

    @Test
    void taunt_shouldRedirectSingleTargetSkill() {
        GameConfigRegistry registry = buildRegistry(0);
        BattleEngine engine = engine(registry);
        BattleUnit player = active(unit("P1", "WATER", 200, 50, 50, 50, 50, 100, "SKILL_HIT"), 0);
        BattleUnit enemy1 = active(unit("E1", "FIRE", 200, 40, 40, 50, 50, 10, "SKILL_WAIT"), 0);
        BattleUnit enemy2 = active(unit("E2", "FIRE", 200, 40, 40, 50, 50, 10, "SKILL_WAIT"), 1);
        enemy2.getStatuses().add(new StatusInstance("TAUNT", 2, "E2"));
        BattleContext ctx = context(15L, List.of(player), List.of(enemy1, enemy2));

        TurnResult result = engine.playTurn(ctx, List.of(BattleAction.skill("P1", "SKILL_HIT", "E1")));

        BattleEvent damage = findEvent(result.getEvents(), BattleEventType.DAMAGE);
        assertNotNull(damage);
        assertEquals("E2", damage.getTargetId(), "单体技能应被嘲讽单位重定向");
        assertEquals(200, enemy1.getCurrentHp(), "原目标不应受到伤害");
    }

    // ---- 被动 / 胜负 ----

    @Test
    void surviveLethalPassive_shouldKeepOneHpOnce() {
        GameConfigRegistry registry = buildRegistry(0);
        DecisionProvider killer = (ctx, side) -> {
            List<BattleUnit> targets = ctx.getPlayerSide().getActiveAliveUnits();
            return side.getActiveAliveUnits().stream()
                    .map(u -> targets.isEmpty() ? BattleAction.defend(u.getUnitId())
                            : BattleAction.skill(u.getUnitId(), "SKILL_BIG", targets.get(0).getUnitId()))
                    .toList();
        };
        BattleEngine engine = engine(registry, killer);
        BattleUnit player = active(unit("P1", "WATER", 200, 50, 50, 50, 50, 10, "SKILL_WAIT"), 0);
        player.setPassives(new ArrayList<>(List.of(registry.getPassive("PASSIVE_UNYIELDING"))));
        BattleUnit enemy = active(unit("E1", "FIRE", 500, 50, 50, 50, 50, 100, "SKILL_BIG"), 0);
        BattleContext ctx = context(16L, List.of(player), List.of(enemy));

        TurnResult round1 = engine.playTurn(ctx, List.of(BattleAction.defend("P1")));
        assertTrue(player.isAlive(), "不屈被动应保留 1 点生命");
        assertEquals(1, player.getCurrentHp());
        assertNotNull(findEvent(round1.getEvents(), BattleEventType.PASSIVE_TRIGGERED));

        TurnResult round2 = engine.playTurn(ctx, List.of(BattleAction.defend("P1")));
        assertFalse(player.isAlive(), "不屈每场仅一次，第二次致命伤害应倒下");
        assertNotNull(findEvent(round2.getEvents(), BattleEventType.PET_DEFEATED));
    }

    @Test
    void battleEnd_allEnemiesDefeated_shouldFinishWithPlayerWin() {
        GameConfigRegistry registry = buildRegistry(0);
        BattleEngine engine = engine(registry);
        BattleUnit player = active(unit("P1", "WATER", 200, 50, 50, 50, 50, 100, "SKILL_BIG"), 0);
        BattleUnit enemy = active(unit("E1", "FIRE", 100, 40, 40, 50, 50, 10, "SKILL_WAIT"), 0);
        BattleContext ctx = context(17L, List.of(player), List.of(enemy));

        TurnResult result = engine.playTurn(ctx, List.of(BattleAction.skill("P1", "SKILL_BIG", "E1")));

        assertTrue(result.isFinished(), "敌方全灭应结束战斗");
        assertEquals("PLAYER", result.getWinner());
        BattleEvent end = findEvent(result.getEvents(), BattleEventType.BATTLE_ENDED);
        assertNotNull(end);
        assertEquals("PLAYER", end.getData().get("winner"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> engine.playTurn(ctx, List.of(BattleAction.defend("P1"))));
        assertEquals("BATTLE_FINISHED", ex.getErrorCode(), "结束后不能再行动");
    }

    // ---- 开局倒下（需求 §45：战斗后倒下保持 0HP，参战仍为 0） ----

    @Test
    void startBattle_withZeroHpActivePlayerUnit_shouldFallAndBeReplacedByBench() {
        GameConfigRegistry registry = buildRegistry(0);
        BattleEngine engine = engine(registry);
        BattleUnit p1 = active(unit("P1", "WATER", 200, 50, 50, 50, 50, 100, "SKILL_WAIT"), 0);
        p1.setCurrentHp(0);  // 上场即倒下（HP 跨战斗保留）
        BattleUnit p2 = unit("P2", "WATER", 200, 50, 50, 50, 50, 90, "SKILL_WAIT");  // 候补满血
        BattleUnit enemy = active(unit("E1", "FIRE", 200, 40, 40, 50, 50, 10, "SKILL_WAIT"), 0);
        BattleContext ctx = context(18L, List.of(p1, p2), List.of(enemy));

        engine.startBattle(ctx);

        assertFalse(p1.isAlive(), "0HP 上场单位应开局倒下");
        assertTrue(p2.isActive(), "候补应立即补位上场");
        assertFalse(ctx.isFinished(), "玩家仍有存活单位，战斗不应结束");
    }

    @Test
    void startBattle_withAllPlayerUnitsZeroHp_shouldEndImmediatelyAsEnemyWin() {
        GameConfigRegistry registry = buildRegistry(0);
        BattleEngine engine = engine(registry);
        BattleUnit p1 = active(unit("P1", "WATER", 200, 50, 50, 50, 50, 100, "SKILL_WAIT"), 0);
        p1.setCurrentHp(0);  // 唯一上场单位倒下且无候补
        BattleUnit enemy = active(unit("E1", "FIRE", 200, 40, 40, 50, 50, 10, "SKILL_WAIT"), 0);
        BattleContext ctx = context(19L, List.of(p1), List.of(enemy));

        engine.startBattle(ctx);

        assertTrue(ctx.isFinished(), "玩家全灭应开局直接判负");
        assertEquals("ENEMY", ctx.getWinner());
    }

    // ---- 辅助 ----

    private BattleEvent findEvent(List<BattleEvent> events, BattleEventType type) {
        return events.stream().filter(e -> e.getType() == type).findFirst().orElse(null);
    }
}
