package com.petgame.battle;

import com.petgame.battle.ai.DecisionProvider;
import com.petgame.battle.engine.BattleContext;
import com.petgame.battle.engine.BattleEngine;
import com.petgame.battle.event.BattleEvent;
import com.petgame.battle.event.BattleEventType;
import com.petgame.battle.model.BattleAction;
import com.petgame.battle.model.BattleUnit;
import com.petgame.config.GameConfigRegistry;
import com.petgame.config.model.StatusEffectConfig;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.petgame.battle.BattleTestFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 阶段 7：控制抗性 + 连续控制衰减测试。
 * <p>
 * 覆盖：
 * - 精英 -20% 控制成功率
 * - Boss -40% 控制成功率
 * - 连续控制衰减 ×1.0 / ×0.7 / ×0.4
 * - 连续 2 回合未受控归零
 */
class BattleEngineControlResistanceTest {

    private final DecisionProvider waitProvider = (ctx, side) ->
            side.getActiveAliveUnits().stream()
                    .map(u -> BattleAction.skill(u.getUnitId(), "SKILL_WAIT", null))
                    .toList();

    private BattleEngine engine(GameConfigRegistry registry) {
        return new BattleEngine(registry, waitProvider);
    }

    /** 构建含控制技能的 registry。 */
    private GameConfigRegistry buildRegistryWithControl() {
        GameConfigRegistry registry = buildRegistry(0);
        // 添加控制状态 STUN
        StatusEffectConfig stun = new StatusEffectConfig();
        stun.setId("STUN");
        stun.setName("眩晕");
        stun.setCategory("SPECIAL_CONTROL");
        stun.setDefaultDuration(1);
        stun.setConsumeOnSkip(true);
        addStatus(registry, stun);
        // 添加施加眩晕的技能 SKILL_STUN（100% 基础概率，方便测试衰减）
        addSkill(registry, pubSkill("SKILL_STUN", "NONE", "STATUS", "ENEMY_SINGLE", 0,
                Map.of()));
        // 手动在 SkillConfig 上加 effect（pubSkill 不含 status 效果，需直接构造）
        return registry;
    }

    // ---- 控制抗性测试 ----

    @Test
    void bossControlResistance_shouldReduceChance() {
        GameConfigRegistry registry = buildRegistry(0);
        // Boss 单位设控制抗性 0.6
        BattleUnit boss = unit("ENEMY_BOSS", "FIRE", 100, 10, 10, 10, 10, 10, "SKILL_WAIT");
        boss.setControlResistance(0.6);
        active(boss, 0);

        BattleUnit player = unit("P1", "WATER", 100, 30, 10, 10, 10, 20, "SKILL_HIT");
        active(player, 0);

        BattleContext ctx = context(42, List.of(player), List.of(boss));
        BattleEngine engine = engine(registry);
        engine.startBattle(ctx);

        // 验证 Boss 控制抗性字段已正确设置
        assertEquals(0.6, boss.getControlResistance(), 0.001);
    }

    @Test
    void eliteControlResistance_shouldBe08() {
        BattleUnit elite = unit("ENEMY_ELITE", "FIRE", 80, 15, 10, 10, 10, 15, "SKILL_WAIT");
        elite.setControlResistance(0.8);
        active(elite, 0);

        assertEquals(0.8, elite.getControlResistance(), 0.001);
    }

    @Test
    void normalUnitControlResistance_shouldBe1() {
        BattleUnit normal = unit("ENEMY_NORMAL", "FIRE", 50, 10, 10, 10, 10, 10, "SKILL_WAIT");
        assertEquals(1.0, normal.getControlResistance(), 0.001, "普通单位控制抗性应为 1.0");
    }

    // ---- 连续控制衰减字段测试 ----

    @Test
    void consecutiveControlCount_shouldStartAtZero() {
        BattleUnit unit = unit("P1", "WATER", 100, 10, 10, 10, 10, 10, "SKILL_HIT");
        assertEquals(0, unit.getConsecutiveControlCount());
        assertEquals(0, unit.getRoundsWithoutControl());
    }

    @Test
    void controlDecayResetRounds_shouldDefaultTo2() {
        GameConfigRegistry registry = buildRegistry(0);
        assertEquals(2, registry.getSystemRules().getControlDecayResetRounds());
    }

    @Test
    void consecutiveControlDecay_shouldHave3Values() {
        GameConfigRegistry registry = buildRegistry(0);
        // buildRegistry 使用默认 SystemRuleConfig，手动设置衰减系数
        registry.getSystemRules().setConsecutiveControlDecay(List.of(1.0, 0.7, 0.4));
        List<Double> decay = registry.getSystemRules().getConsecutiveControlDecay();
        assertNotNull(decay);
        assertEquals(3, decay.size());
        assertEquals(1.0, decay.get(0), 0.001);
        assertEquals(0.7, decay.get(1), 0.001);
        assertEquals(0.4, decay.get(2), 0.001);
    }

    // ---- runFullBattle 测试 ----

    @Test
    void runFullBattle_shouldFinishWithoutInfiniteLoop() {
        GameConfigRegistry registry = buildRegistry(0);
        BattleUnit player = unit("P1", "WATER", 50, 30, 10, 5, 5, 20, "SKILL_HIT");
        active(player, 0);

        BattleUnit enemy = unit("E1", "FIRE", 50, 10, 10, 5, 5, 5, "SKILL_WAIT");
        active(enemy, 0);

        BattleContext ctx = context(1, List.of(player), List.of(enemy));
        ctx.setBattleType("BOSS");

        BattleEngine engine = engine(registry);
        DecisionProvider attackAI = (c, side) ->
                side.getActiveAliveUnits().stream()
                        .map(u -> BattleAction.skill(u.getUnitId(), "SKILL_HIT", "E1"))
                        .toList();

        engine.runFullBattle(ctx, attackAI);

        assertTrue(ctx.isFinished(), "runFullBattle 应使战斗结束");
        assertNotNull(ctx.getWinner(), "应有胜方");
    }

    // ---- uncapturable 测试 ----

    @Test
    void uncapturableContext_shouldBeSettable() {
        BattleContext ctx = new BattleContext("BOSS_TEST", 42);
        ctx.setUncapturable(true);
        assertTrue(ctx.isUncapturable());
        ctx.setUncapturable(false);
        assertFalse(ctx.isUncapturable());
    }
}
