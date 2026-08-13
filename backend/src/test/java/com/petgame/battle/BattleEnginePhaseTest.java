package com.petgame.battle;

import com.petgame.battle.ai.DecisionProvider;
import com.petgame.battle.engine.BattleContext;
import com.petgame.battle.engine.BattleEngine;
import com.petgame.battle.event.BattleEventType;
import com.petgame.battle.model.BattleAction;
import com.petgame.battle.model.BattleUnit;
import com.petgame.config.GameConfigRegistry;
import com.petgame.config.model.BossesConfig;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.petgame.battle.BattleTestFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 阶段 7：Boss 阶段触发机制测试。
 * <p>
 * 覆盖：
 * - HP 阈值触发（50% 以下添加技能）
 * - 多阶段触发（50% 和 25%）
 * - 效果类型 ADD_SKILL / ADD_SHIELD
 * - 阶段只触发一次（activated 标记）
 */
class BattleEnginePhaseTest {

    private final DecisionProvider waitProvider = (ctx, side) ->
            side.getActiveAliveUnits().stream()
                    .map(u -> BattleAction.skill(u.getUnitId(), "SKILL_WAIT", null))
                    .toList();

    private BattleEngine engine(GameConfigRegistry registry) {
        return new BattleEngine(registry, waitProvider);
    }

    /** 创建阶段触发器。 */
    private BossesConfig.PhaseTrigger phaseTrigger(double hpPercent, BossesConfig.PhaseEffect... effects) {
        BossesConfig.PhaseTrigger trigger = new BossesConfig.PhaseTrigger();
        trigger.setHpPercent(hpPercent);
        trigger.setEffects(List.of(effects));
        return trigger;
    }

    private BossesConfig.PhaseEffect addSkillEffect(String skillId) {
        BossesConfig.PhaseEffect effect = new BossesConfig.PhaseEffect();
        effect.setType("ADD_SKILL");
        effect.setSkillId(skillId);
        return effect;
    }

    private BossesConfig.PhaseEffect addShieldEffect(int shieldValue) {
        BossesConfig.PhaseEffect effect = new BossesConfig.PhaseEffect();
        effect.setType("ADD_SHIELD");
        effect.setShieldValue(shieldValue);
        return effect;
    }

    @Test
    void phaseTrigger_shouldAddSkillWhenHpBelow50Percent() {
        GameConfigRegistry registry = buildRegistry(0);

        // Boss 100 HP，50% 阈值获得 SKILL_BIG
        BattleUnit boss = unit("BOSS", "FIRE", 100, 15, 10, 10, 10, 10, "SKILL_WAIT");
        active(boss, 0);
        boss.setPhaseTriggers(new ArrayList<>(List.of(
                phaseTrigger(0.50, addSkillEffect("SKILL_BIG"))
        )));
        boss.setPhaseActivated(new ArrayList<>(List.of(false)));

        BattleUnit player = unit("P1", "WATER", 100, 50, 10, 5, 5, 30, "SKILL_BIG", "SKILL_WAIT");
        active(player, 0);

        BattleContext ctx = context(42, List.of(player), List.of(boss));
        BattleEngine engine = engine(registry);
        engine.startBattle(ctx);

        // Boss 初始不含 SKILL_BIG
        assertFalse(boss.getSkillIds().contains("SKILL_BIG"), "开始前 Boss 不应有 SKILL_BIG");

        // 把 Boss HP 打到 50 以下
        boss.setCurrentHp(49);

        // 手动触发 checkPhaseTriggers 通过 endRound（提交空行动让引擎处理回合结束）
        engine.playTurn(ctx, List.of(BattleAction.skill("P1", "SKILL_WAIT", null)));

        // 回合结束后阶段触发应该已激活
        assertTrue(boss.getPhaseActivated().get(0), "阶段触发器应已激活");
        assertTrue(boss.getSkillIds().contains("SKILL_BIG"), "Boss 应获得 SKILL_BIG");
    }

    @Test
    void phaseTrigger_shouldAddShieldWhenHpBelowThreshold() {
        GameConfigRegistry registry = buildRegistry(0);

        BattleUnit boss = unit("BOSS", "FIRE", 200, 15, 10, 10, 10, 10, "SKILL_WAIT");
        active(boss, 0);
        boss.setPhaseTriggers(new ArrayList<>(List.of(
                phaseTrigger(0.30, addShieldEffect(50))
        )));
        boss.setPhaseActivated(new ArrayList<>(List.of(false)));

        BattleUnit player = unit("P1", "WATER", 100, 10, 10, 5, 5, 30, "SKILL_HIT", "SKILL_WAIT");
        active(player, 0);

        BattleContext ctx = context(42, List.of(player), List.of(boss));
        BattleEngine engine = engine(registry);
        engine.startBattle(ctx);

        assertEquals(0, boss.getShield());

        // 把 Boss HP 打到 30% 以下（200 × 0.3 = 60）
        boss.setCurrentHp(59);

        engine.playTurn(ctx, List.of(BattleAction.skill("P1", "SKILL_WAIT", null)));

        assertTrue(boss.getPhaseActivated().get(0), "护盾阶段应已激活");
        assertEquals(50, boss.getShield(), "Boss 应获得 50 护盾");
    }

    @Test
    void phaseTrigger_shouldNotTriggerTwice() {
        GameConfigRegistry registry = buildRegistry(0);

        BattleUnit boss = unit("BOSS", "FIRE", 100, 15, 10, 10, 10, 10, "SKILL_WAIT");
        active(boss, 0);
        boss.setPhaseTriggers(new ArrayList<>(List.of(
                phaseTrigger(0.50, addShieldEffect(30))
        )));
        boss.setPhaseActivated(new ArrayList<>(List.of(false)));

        BattleUnit player = unit("P1", "WATER", 100, 10, 10, 5, 5, 30, "SKILL_HIT", "SKILL_WAIT");
        active(player, 0);

        BattleContext ctx = context(42, List.of(player), List.of(boss));
        BattleEngine engine = engine(registry);
        engine.startBattle(ctx);

        boss.setCurrentHp(49);
        engine.playTurn(ctx, List.of(BattleAction.skill("P1", "SKILL_WAIT", null)));

        assertEquals(30, boss.getShield(), "第一次触发应获得 30 护盾");

        // 再打一回合，护盾不应再增加
        boss.setCurrentHp(40);
        engine.playTurn(ctx, List.of(BattleAction.skill("P1", "SKILL_WAIT", null)));

        assertEquals(30, boss.getShield(), "已激活的阶段不应重复触发");
    }

    @Test
    void phaseTrigger_multiPhase_shouldTriggerAtDifferentThresholds() {
        GameConfigRegistry registry = buildRegistry(0);

        BattleUnit boss = unit("BOSS", "FIRE", 200, 15, 10, 10, 10, 10, "SKILL_WAIT");
        active(boss, 0);
        boss.setPhaseTriggers(new ArrayList<>(List.of(
                phaseTrigger(0.50, addShieldEffect(20)),
                phaseTrigger(0.25, addShieldEffect(40))
        )));
        boss.setPhaseActivated(new ArrayList<>(List.of(false, false)));

        BattleUnit player = unit("P1", "WATER", 100, 10, 10, 5, 5, 30, "SKILL_HIT", "SKILL_WAIT");
        active(player, 0);

        BattleContext ctx = context(42, List.of(player), List.of(boss));
        BattleEngine engine = engine(registry);
        engine.startBattle(ctx);

        // 打到 50% 以下
        boss.setCurrentHp(99);
        engine.playTurn(ctx, List.of(BattleAction.skill("P1", "SKILL_WAIT", null)));

        assertTrue(boss.getPhaseActivated().get(0), "第一阶段应触发");
        assertFalse(boss.getPhaseActivated().get(1), "第二阶段不应触发");
        assertEquals(20, boss.getShield());

        // 打到 25% 以下
        boss.setCurrentHp(49);
        engine.playTurn(ctx, List.of(BattleAction.skill("P1", "SKILL_WAIT", null)));

        assertTrue(boss.getPhaseActivated().get(1), "第二阶段应触发");
        assertEquals(60, boss.getShield(), "两阶段护盾叠加应为 60");
    }
}
