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
import com.petgame.config.GameConfigRegistry;
import com.petgame.config.model.SkillConfig;
import com.petgame.config.model.StatusEffectConfig;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.petgame.battle.BattleTestFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 技能与状态体系补充机制测试（REV-020，技术方案 §81/§82）。
 * <p>
 * 覆盖：留生一击（保留 1HP/清 DOT/震慑/暴击不可绕过/已 1HP 不刷新/Boss 不震慑）、
 * 吸血（按实际 HP 损失、护盾不计）、HP 百分比交换（非伤害、Boss 上限）、
 * 混乱（单体改目标）、隐匿（单体不可选中）、反击（单体触发、不连锁）、
 * 标记叠层（maxStack 触发）、再生（回合末恢复、禁疗影响）、
 * 震慑（失去下一次主动行动且不刷新）、被动防递归、技能共享。
 */
class NewMechanicsEngineTest {

    /** 敌方全体空操作。 */
    private final DecisionProvider waitProvider = (ctx, side) ->
            side.getActiveAliveUnits().stream()
                    .map(u -> BattleAction.skill(u.getUnitId(), "SKILL_WAIT", null))
                    .toList();

    /** 构建含补充机制技能与状态的 registry（critRate 0 保证确定性）。 */
    private GameConfigRegistry registry() {
        return registry(0);
    }

    private GameConfigRegistry registry(double critRate) {
        GameConfigRegistry reg = buildRegistry(critRate);

        // ---- 状态 ----
        StatusEffectConfig confusion = new StatusEffectConfig();
        confusion.setId("CONFUSION");
        confusion.setName("混乱");
        confusion.setCategory("CONTINUOUS");
        confusion.setDefaultDuration(2);
        confusion.setConfusion(true);
        addStatus(reg, confusion);

        StatusEffectConfig stealth = new StatusEffectConfig();
        stealth.setId("STEALTH");
        stealth.setName("隐匿");
        stealth.setCategory("CONTINUOUS");
        stealth.setDefaultDuration(2);
        stealth.setStealth(true);
        addStatus(reg, stealth);

        StatusEffectConfig counter = new StatusEffectConfig();
        counter.setId("COUNTER");
        counter.setName("反击");
        counter.setCategory("BUFF");
        counter.setDefaultDuration(2);
        counter.setCounterRate(1.0);
        counter.setCounterValue(10);
        counter.setCounterScaling(0.5);
        addStatus(reg, counter);

        StatusEffectConfig mark = new StatusEffectConfig();
        mark.setId("THUNDER_MARK");
        mark.setName("雷印");
        mark.setCategory("MARK");
        mark.setDefaultDuration(3);
        mark.setStack(true);
        mark.setMaxStack(2);
        mark.setStackTrigger("DAMAGE");
        mark.setStackTriggerValue(50);
        addStatus(reg, mark);

        StatusEffectConfig stun = new StatusEffectConfig();
        stun.setId("CAPTURE_STUN");
        stun.setName("震慑");
        stun.setCategory("SPECIAL_CONTROL");
        stun.setDefaultDuration(1);
        stun.setSkipActionChance(1.0);
        stun.setConsumeOnSkip(true);
        stun.setCaptureStun(true);
        stun.setCaptureBonus(false);
        addStatus(reg, stun);

        StatusEffectConfig regen = new StatusEffectConfig();
        regen.setId("REGEN");
        regen.setName("再生");
        regen.setCategory("CONTINUOUS");
        regen.setDefaultDuration(3);
        regen.setHealPercent(0.10);
        addStatus(reg, regen);

        StatusEffectConfig healBlock = new StatusEffectConfig();
        healBlock.setId("HEAL_BLOCK");
        healBlock.setName("禁疗");
        healBlock.setCategory("DEBUFF");
        healBlock.setDefaultDuration(2);
        healBlock.setHealBlock(true);
        addStatus(reg, healBlock);

        // ---- 技能 ----
        // 留生一击：高伤害 + LEAVE_AT_ONE_HP + 保护时清 DOT + 保护且可捕捉时附加震慑
        SkillConfig leaveOne = pubSkill("SKILL_LEAVE_ONE", "NONE", "DAMAGE", "ENEMY_SINGLE",
                1000, Map.of());
        leaveOne.getEffects().add(effect("LEAVE_AT_ONE_HP", null));
        SkillConfig.SkillEffectConfig removeDot = effect("REMOVE_STATUS", null);
        removeDot.setDotOnly(true);
        removeDot.setOnProtect(true);
        leaveOne.getEffects().add(removeDot);
        SkillConfig.SkillEffectConfig applyStun = effect("APPLY_STATUS", "CAPTURE_STUN");
        applyStun.setOnProtect(true);
        applyStun.setCapturableOnly(true);
        leaveOne.getEffects().add(applyStun);
        addSkill(reg, leaveOne);

        // 吸血技能：伤害 + 50% 吸血
        SkillConfig lifeSteal = pubSkill("SKILL_LIFESTEAL", "NONE", "DAMAGE", "ENEMY_SINGLE",
                60, Map.of("STRENGTH", 1.0));
        SkillConfig.SkillEffectConfig steal = effect("LIFE_STEAL", null);
        steal.setPercent(0.5);
        lifeSteal.getEffects().add(steal);
        addSkill(reg, lifeSteal);

        // HP 百分比交换
        SkillConfig exchange = pubSkill("SKILL_EXCHANGE", "NONE", "NONE", "ENEMY_SINGLE", 0, Map.of());
        exchange.getEffects().add(effect("HP_PERCENT_EXCHANGE", null));
        addSkill(reg, exchange);

        // 施加标记
        SkillConfig markSkill = pubSkill("SKILL_MARK", "NONE", "NONE", "ENEMY_SINGLE", 0, Map.of());
        markSkill.getEffects().add(effect("APPLY_STATUS", "THUNDER_MARK"));
        addSkill(reg, markSkill);

        return reg;
    }

    private SkillConfig.SkillEffectConfig effect(String type, String statusId) {
        SkillConfig.SkillEffectConfig cfg = new SkillConfig.SkillEffectConfig();
        cfg.setType(type);
        cfg.setStatusId(statusId);
        return cfg;
    }

    private BattleEngine engine(GameConfigRegistry registry) {
        return new BattleEngine(registry, waitProvider);
    }

    private BattleEvent findEvent(List<BattleEvent> events, BattleEventType type) {
        return events.stream().filter(e -> e.getType() == type).findFirst().orElse(null);
    }

    private boolean hasEvent(List<BattleEvent> events, BattleEventType type) {
        return events.stream().anyMatch(e -> e.getType() == type);
    }

    // ==================== 留生一击 ====================

    @Test
    void leaveAtOneHp_capturableTarget_keepsOneHp_clearsDot_appliesStun() {
        GameConfigRegistry registry = registry();
        BattleEngine engine = engine(registry);
        BattleUnit player = active(unit("P1", "WATER", 200, 50, 50, 50, 50, 100, "SKILL_LEAVE_ONE"), 0);
        BattleUnit enemy = active(unit("E1", "FIRE", 100, 40, 40, 50, 50, 10, "SKILL_WAIT"), 0);
        enemy.setWildData(new BattleUnit.WildUnitData()); // 可捕捉
        enemy.getStatuses().add(new StatusInstance("BURN", 2, "P1"));
        BattleContext ctx = context(10L, List.of(player), List.of(enemy));

        TurnResult result = engine.playTurn(ctx, List.of(BattleAction.skill("P1", "SKILL_LEAVE_ONE", "E1")));

        assertTrue(enemy.isAlive(), "留生一击不应击杀可捕捉目标");
        assertEquals(1, enemy.getCurrentHp(), "致死伤害应保留 1HP");
        assertFalse(enemy.hasStatus("BURN"), "触发保护时应清除持续伤害状态");
        assertTrue(hasEvent(result.getEvents(), BattleEventType.STUNNED), "应发出震慑事件");
        // 震慑当回合即消耗于跳过敌方行动（consumeOnSkip，需求 §142.4 至少失去下一次主动行动）
        assertTrue(hasEvent(result.getEvents(), BattleEventType.ACTION_SKIPPED),
                "震慑应使目标失去当回合主动行动");
        assertFalse(enemy.hasStatus("CAPTURE_STUN"), "震慑在跳过行动后应被消耗移除");
    }

    @Test
    void leaveAtOneHp_nonCapturable_keepsOneHpButNoStun() {
        GameConfigRegistry registry = registry();
        BattleEngine engine = engine(registry);
        BattleUnit player = active(unit("P1", "WATER", 200, 50, 50, 50, 50, 100, "SKILL_LEAVE_ONE"), 0);
        BattleUnit enemy = active(unit("E1", "FIRE", 100, 40, 40, 50, 50, 10, "SKILL_WAIT"), 0);
        // 无 wildData → 测试战斗敌方不可捕捉（等价 Boss 语义：不附加震慑）
        BattleContext ctx = context(11L, List.of(player), List.of(enemy));

        TurnResult result = engine.playTurn(ctx, List.of(BattleAction.skill("P1", "SKILL_LEAVE_ONE", "E1")));

        assertEquals(1, enemy.getCurrentHp(), "仍应保留 1HP");
        assertFalse(enemy.hasStatus("CAPTURE_STUN"), "不可捕捉目标不附加震慑（需求 §142.5）");
        assertFalse(hasEvent(result.getEvents(), BattleEventType.STUNNED));
    }

    @Test
    void leaveAtOneHp_targetAlreadyOneHp_fallsWithoutRefresh() {
        GameConfigRegistry registry = registry();
        BattleEngine engine = engine(registry);
        BattleUnit player = active(unit("P1", "WATER", 200, 50, 50, 50, 50, 100, "SKILL_LEAVE_ONE"), 0);
        BattleUnit enemy = active(unit("E1", "FIRE", 100, 40, 40, 50, 50, 10, "SKILL_WAIT"), 0);
        enemy.setWildData(new BattleUnit.WildUnitData());
        enemy.setCurrentHp(1);
        BattleContext ctx = context(12L, List.of(player), List.of(enemy));

        TurnResult result = engine.playTurn(ctx, List.of(BattleAction.skill("P1", "SKILL_LEAVE_ONE", "E1")));

        assertFalse(enemy.isAlive(), "已处于 1HP 的目标再次命中应被击败（需求 §142.4 不无限刷新）");
        assertFalse(hasEvent(result.getEvents(), BattleEventType.STUNNED));
    }

    @Test
    void leaveAtOneHp_critCannotBypass() {
        GameConfigRegistry registry = registry(1.0); // 必定暴击
        BattleEngine engine = engine(registry);
        BattleUnit player = active(unit("P1", "WATER", 200, 50, 50, 50, 50, 100, "SKILL_LEAVE_ONE"), 0);
        BattleUnit enemy = active(unit("E1", "FIRE", 100, 40, 40, 50, 50, 10, "SKILL_WAIT"), 0);
        enemy.setWildData(new BattleUnit.WildUnitData());
        BattleContext ctx = context(13L, List.of(player), List.of(enemy));

        engine.playTurn(ctx, List.of(BattleAction.skill("P1", "SKILL_LEAVE_ONE", "E1")));

        assertTrue(enemy.isAlive());
        assertEquals(1, enemy.getCurrentHp(), "暴击不能绕过留生一击保护（需求 §142.2）");
    }

    // ==================== 吸血 ====================

    @Test
    void lifeSteal_basedOnActualHpLoss_shieldNotCounted() {
        GameConfigRegistry registry = registry();
        BattleEngine engine = engine(registry);
        BattleUnit player = active(unit("P1", "WATER", 200, 50, 50, 50, 50, 100, "SKILL_LIFESTEAL"), 0);
        player.setCurrentHp(100); // 先压血以便观察恢复
        BattleUnit enemy = active(unit("E1", "FIRE", 200, 40, 40, 50, 50, 10, "SKILL_WAIT"), 0);
        enemy.setShield(50);
        BattleContext ctx = context(20L, List.of(player), List.of(enemy));

        TurnResult result = engine.playTurn(ctx, List.of(BattleAction.skill("P1", "SKILL_LIFESTEAL", "E1")));

        // 基础 60 + 力量 50 = 110；防御减伤 110×200/250 = 88；护盾吸收 50 → 实际 HP 损失 38
        assertEquals(162, enemy.getCurrentHp(), "实际 HP 损失应为 38（护盾吸收不计）");
        assertEquals(0, enemy.getShield());
        BattleEvent stealEvent = findEvent(result.getEvents(), BattleEventType.LIFE_STEAL);
        assertNotNull(stealEvent, "应发出吸血事件");
        assertEquals(19, stealEvent.getValue(), "吸血 = 实际损失 38 × 50% = 19（需求 §143）");
        assertEquals(119, player.getCurrentHp());
    }

    // ==================== HP 百分比交换 ====================

    @Test
    void hpExchange_capturableTarget_fullSwap_notDamage() {
        GameConfigRegistry registry = registry();
        BattleEngine engine = engine(registry);
        BattleUnit player = active(unit("P1", "WATER", 200, 50, 50, 50, 50, 100, "SKILL_EXCHANGE"), 0);
        player.setCurrentHp(40); // 20%
        BattleUnit enemy = active(unit("E1", "FIRE", 200, 40, 40, 50, 50, 10, "SKILL_WAIT"), 0);
        enemy.setWildData(new BattleUnit.WildUnitData()); // 可捕捉目标不受 Boss 上限约束
        enemy.setCurrentHp(180); // 90%
        BattleContext ctx = context(30L, List.of(player), List.of(enemy));

        TurnResult result = engine.playTurn(ctx, List.of(BattleAction.skill("P1", "SKILL_EXCHANGE", "E1")));

        assertEquals(180, player.getCurrentHp(), "交换后玩家应为 90%");
        assertEquals(40, enemy.getCurrentHp(), "交换后敌方应为 20%");
        assertTrue(hasEvent(result.getEvents(), BattleEventType.HP_PERCENT_EXCHANGED));
        assertFalse(hasEvent(result.getEvents(), BattleEventType.DAMAGE), "HP 交换非伤害");
        assertFalse(hasEvent(result.getEvents(), BattleEventType.CRITICAL), "HP 交换不触发暴击");
        assertFalse(hasEvent(result.getEvents(), BattleEventType.LIFE_STEAL), "HP 交换不触发吸血");
    }

    @Test
    void hpExchange_bossLikeTarget_cappedByLimit() {
        GameConfigRegistry registry = registry();
        BattleEngine engine = engine(registry);
        BattleUnit player = active(unit("P1", "WATER", 200, 50, 50, 50, 50, 100, "SKILL_EXCHANGE"), 0);
        player.setCurrentHp(40); // 20%
        BattleUnit enemy = active(unit("E1", "FIRE", 200, 40, 40, 50, 50, 10, "SKILL_WAIT"), 0);
        // 无 wildData → 按 Boss 类单位处理，交换幅度上限 0.20
        enemy.setCurrentHp(180); // 90%
        BattleContext ctx = context(31L, List.of(player), List.of(enemy));

        engine.playTurn(ctx, List.of(BattleAction.skill("P1", "SKILL_EXCHANGE", "E1")));

        // 差值 70% 被限幅到 20%：玩家 20%→40%，敌方 90%→70%
        assertEquals(80, player.getCurrentHp(), "Boss 交换幅度受 0.20 上限约束（需求 §147）");
        assertEquals(140, enemy.getCurrentHp());
    }

    // ==================== 混乱 ====================

    @Test
    void confusion_singleTargetCanRedirect_groupUnaffected() {
        GameConfigRegistry registry = registry();
        boolean redirected = false;
        boolean kept = false;
        // 扫描固定种子：混乱应既能改变目标也能保持原目标（随机性验证）
        for (long seed = 0; seed < 80 && !(redirected && kept); seed++) {
            BattleEngine engine = engine(registry);
            BattleUnit player = active(unit("P1", "WATER", 200, 50, 50, 50, 50, 100, "SKILL_HIT"), 0);
            BattleUnit ally = active(unit("P2", "WATER", 200, 50, 50, 50, 50, 90, "SKILL_WAIT"), 1);
            BattleUnit enemy = active(unit("E1", "FIRE", 200, 40, 40, 50, 50, 10, "SKILL_WAIT"), 0);
            player.getStatuses().add(new StatusInstance("CONFUSION", 2, "E1"));
            BattleContext ctx = context(seed, List.of(player, ally), List.of(enemy));

            TurnResult result = engine.playTurn(ctx, List.of(BattleAction.skill("P1", "SKILL_HIT", "E1")));

            if (hasEvent(result.getEvents(), BattleEventType.CONFUSED_TARGET_CHANGED)) {
                redirected = true;
                assertEquals(200, enemy.getCurrentHp(), "改向后原目标不受伤害");
                assertTrue(ally.getCurrentHp() < 200, "改向后伤害落在友方");
            } else {
                kept = true;
                assertTrue(enemy.getCurrentHp() < 200, "未改向时正常命中原目标");
            }
        }
        assertTrue(redirected, "混乱应在部分种子下改变单体目标（需求 §144.1）");
        assertTrue(kept, "混乱也可能不改变目标");
    }

    // ==================== 隐匿 ====================

    @Test
    void stealth_singleTargetRetargets_nonStealthedUnit() {
        GameConfigRegistry registry = registry();
        BattleEngine engine = engine(registry);
        BattleUnit player = active(unit("P1", "WATER", 200, 50, 50, 50, 50, 100, "SKILL_HIT"), 0);
        BattleUnit stealthed = active(unit("E1", "FIRE", 200, 40, 40, 50, 50, 10, "SKILL_WAIT"), 0);
        stealthed.getStatuses().add(new StatusInstance("STEALTH", 2, "E1"));
        BattleUnit normal = active(unit("E2", "FIRE", 200, 40, 40, 50, 50, 9, "SKILL_WAIT"), 1);
        BattleContext ctx = context(40L, List.of(player), List.of(stealthed, normal));

        engine.playTurn(ctx, List.of(BattleAction.skill("P1", "SKILL_HIT", "E1")));

        assertEquals(200, stealthed.getCurrentHp(), "隐匿单位不应被单体技能选中（需求 §144.5）");
        assertTrue(normal.getCurrentHp() < 200, "伤害应落在非隐匿单位");
    }

    // ==================== 反击 ====================

    @Test
    void counter_singleTargetTriggers_noChain() {
        GameConfigRegistry registry = registry();
        BattleEngine engine = engine(registry);
        BattleUnit player = active(unit("P1", "WATER", 200, 50, 50, 50, 50, 100, "SKILL_HIT"), 0);
        BattleUnit enemy = active(unit("E1", "FIRE", 500, 40, 40, 50, 50, 10, "SKILL_WAIT"), 0);
        enemy.getStatuses().add(new StatusInstance("COUNTER", 2, "E1"));
        BattleContext ctx = context(50L, List.of(player), List.of(enemy));

        TurnResult result = engine.playTurn(ctx, List.of(BattleAction.skill("P1", "SKILL_HIT", "E1")));

        assertTrue(hasEvent(result.getEvents(), BattleEventType.COUNTER_TRIGGERED), "单体伤害应触发反击");
        assertTrue(player.getCurrentHp() < 200, "反击应对攻击者造成伤害");
        long counterCount = result.getEvents().stream()
                .filter(e -> e.getType() == BattleEventType.COUNTER_TRIGGERED).count();
        assertEquals(1, counterCount, "反击不能再次触发反击（需求 §144.2）");
    }

    // ==================== 标记叠层 ====================

    @Test
    void markStack_reachesMax_triggersDamageAndClears() {
        GameConfigRegistry registry = registry();
        BattleEngine engine = engine(registry);
        BattleUnit player = active(unit("P1", "WATER", 200, 50, 50, 50, 50, 100, "SKILL_MARK"), 0);
        BattleUnit enemy = active(unit("E1", "FIRE", 500, 40, 40, 50, 50, 10, "SKILL_WAIT"), 0);
        BattleContext ctx = context(60L, List.of(player), List.of(enemy));

        engine.playTurn(ctx, List.of(BattleAction.skill("P1", "SKILL_MARK", "E1")));
        StatusInstance mark = enemy.getStatuses().stream()
                .filter(s -> s.getStatusId().equals("THUNDER_MARK")).findFirst().orElse(null);
        assertNotNull(mark, "第一次施加标记");
        assertEquals(1, mark.getStack());

        TurnResult second = engine.playTurn(ctx, List.of(BattleAction.skill("P1", "SKILL_MARK", "E1")));
        assertTrue(hasEvent(second.getEvents(), BattleEventType.MARK_STACK_CHANGED), "应发出叠层变化事件");
        assertFalse(enemy.hasStatus("THUNDER_MARK"), "达到最大层数后应触发并清空（需求 §144.6）");
        assertEquals(450, enemy.getCurrentHp(), "叠层触发应造成 50 点伤害");
    }

    // ==================== 再生与禁疗 ====================

    @Test
    void regen_endRoundHeals_respectsHealBlock() {
        GameConfigRegistry registry = registry();
        BattleEngine engine = engine(registry);
        BattleUnit player = active(unit("P1", "WATER", 200, 50, 50, 50, 50, 100, "SKILL_WAIT"), 0);
        player.setCurrentHp(100);
        player.getStatuses().add(new StatusInstance("REGEN", 3, "P1"));
        BattleUnit enemy = active(unit("E1", "FIRE", 200, 40, 40, 50, 50, 10, "SKILL_WAIT"), 0);
        BattleContext ctx = context(70L, List.of(player), List.of(enemy));

        engine.playTurn(ctx, List.of(BattleAction.skill("P1", "SKILL_WAIT", null)));

        assertEquals(120, player.getCurrentHp(), "再生应在回合结束恢复 10% 最大 HP（需求 §144.3）");
    }

    @Test
    void regen_blockedByHealBlock() {
        GameConfigRegistry registry = registry();
        BattleEngine engine = engine(registry);
        BattleUnit player = active(unit("P1", "WATER", 200, 50, 50, 50, 50, 100, "SKILL_WAIT"), 0);
        player.setCurrentHp(100);
        player.getStatuses().add(new StatusInstance("REGEN", 3, "P1"));
        player.getStatuses().add(new StatusInstance("HEAL_BLOCK", 2, "E1"));
        BattleUnit enemy = active(unit("E1", "FIRE", 200, 40, 40, 50, 50, 10, "SKILL_WAIT"), 0);
        BattleContext ctx = context(71L, List.of(player), List.of(enemy));

        engine.playTurn(ctx, List.of(BattleAction.skill("P1", "SKILL_WAIT", null)));

        assertEquals(100, player.getCurrentHp(), "禁疗应阻止再生恢复（需求 §143/§144.3）");
    }

    // ==================== 震慑：安全捕捉窗口 ====================

    @Test
    void captureStun_targetLosesNextAction_thenConsumed() {
        GameConfigRegistry registry = registry();
        // 敌方 AI 试图攻击；被震慑后应跳过
        DecisionProvider enemyAttack = (ctx, side) ->
                side.getActiveAliveUnits().stream()
                        .map(u -> BattleAction.skill(u.getUnitId(), "SKILL_HIT", "P1"))
                        .toList();
        BattleEngine engine = new BattleEngine(registry, enemyAttack);
        BattleUnit player = active(unit("P1", "WATER", 200, 50, 50, 50, 50, 10, "SKILL_WAIT"), 0);
        BattleUnit enemy = active(unit("E1", "FIRE", 200, 40, 40, 50, 50, 100, "SKILL_HIT"), 0);
        enemy.setWildData(new BattleUnit.WildUnitData());
        enemy.getStatuses().add(new StatusInstance("CAPTURE_STUN", 1, "P1"));
        BattleContext ctx = context(80L, List.of(player), List.of(enemy));

        TurnResult result = engine.playTurn(ctx, List.of(BattleAction.skill("P1", "SKILL_WAIT", null)));

        assertEquals(200, player.getCurrentHp(), "震慑期间目标不能主动行动（需求 §142.4 安全窗口）");
        assertTrue(hasEvent(result.getEvents(), BattleEventType.ACTION_SKIPPED));
        assertFalse(enemy.hasStatus("CAPTURE_STUN"), "震慑应在跳过后消耗移除（consumeOnSkip）");
    }

    // ==================== 被动防递归 ====================

    @Test
    void passiveRecursion_depthLimited_noInfiniteLoop() {
        GameConfigRegistry registry = registry();
        // 构造自触发链：被动在 ON_STATUS_APPLIED 时给自己施加 CHAIN 状态 → 再次触发 → 深度上限截断
        StatusEffectConfig chain = new StatusEffectConfig();
        chain.setId("CHAIN");
        chain.setName("连锁");
        chain.setCategory("DEBUFF");
        chain.setDefaultDuration(2);
        addStatus(registry, chain);

        com.petgame.config.model.PassiveSkillConfig loopPassive = new com.petgame.config.model.PassiveSkillConfig();
        loopPassive.setId("PASSIVE_LOOP");
        loopPassive.setName("连锁被动");
        loopPassive.setTrigger("ON_STATUS_APPLIED");
        loopPassive.setEffectType("APPLY_STATUS_SELF");
        loopPassive.setStatusId("CHAIN");
        addPassive(registry, loopPassive);

        BattleEngine engine = engine(registry);
        BattleUnit player = active(unit("P1", "WATER", 200, 50, 50, 50, 50, 100, "SKILL_MARK"), 0);
        player.getPassives().add(loopPassive);
        BattleUnit enemy = active(unit("E1", "FIRE", 500, 40, 40, 50, 50, 10, "SKILL_WAIT"), 0);
        BattleContext ctx = context(90L, List.of(player), List.of(enemy));

        // 施加标记触发 ON_STATUS_APPLIED → 被动自施加 CHAIN → 递归；深度上限应截断且不抛异常
        assertDoesNotThrow(() ->
                engine.playTurn(ctx, List.of(BattleAction.skill("P1", "SKILL_MARK", "E1"))),
                "被动递归必须被触发深度上限截断（技术方案 §78）");
    }

    private void addPassive(GameConfigRegistry registry, com.petgame.config.model.PassiveSkillConfig cfg) {
        try {
            java.lang.reflect.Field field = GameConfigRegistry.class.getDeclaredField("passiveIndex");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, com.petgame.config.model.PassiveSkillConfig> index =
                    (Map<String, com.petgame.config.model.PassiveSkillConfig>) field.get(registry);
            index.put(cfg.getId(), cfg);
        } catch (Exception e) {
            throw new IllegalStateException("追加测试被动失败: " + cfg.getId(), e);
        }
    }

    // ==================== 技能共享 ====================

    @Test
    void maxOf_baseValue_usesHigherStat() {
        // 基础值 + maxOfCoefficient × MAX(力量, 灵力)（技术方案 §25，留生一击等通用技能）
        SkillConfig cfg = pubSkill("SKILL_MAXOF_TEST", "NONE", "DAMAGE", "ENEMY_SINGLE", 50, Map.of());
        cfg.setMaxOf(List.of("STRENGTH", "SPIRIT"));
        cfg.setMaxOfCoefficient(1.0);
        BattleUnit caster = unit("C1", "WATER", 100, 80, 30, 10, 10, 10);
        assertEquals(130.0, com.petgame.battle.calculator.DamageCalculator.computeBaseValue(cfg, caster), 0.001);
    }

    @Test
    void skillSharing_sameSkillIdUsedByBothSides_works() {
        GameConfigRegistry registry = registry();
        DecisionProvider enemyUsesShared = (ctx, side) ->
                side.getActiveAliveUnits().stream()
                        .map(u -> BattleAction.skill(u.getUnitId(), "SKILL_HIT", "P1"))
                        .toList();
        BattleEngine engine = new BattleEngine(registry, enemyUsesShared);
        // 双方引用同一个 skillId（技能共享，需求 §149）
        BattleUnit player = active(unit("P1", "WATER", 500, 50, 50, 50, 50, 100, "SKILL_HIT"), 0);
        BattleUnit enemy = active(unit("E1", "FIRE", 500, 40, 40, 50, 50, 10, "SKILL_HIT"), 0);
        BattleContext ctx = context(95L, List.of(player), List.of(enemy));

        engine.playTurn(ctx, List.of(BattleAction.skill("P1", "SKILL_HIT", "E1")));

        assertTrue(player.getCurrentHp() < 500, "敌方使用共享技能应正常生效");
        assertTrue(enemy.getCurrentHp() < 500, "我方使用共享技能应正常生效");
    }
}
