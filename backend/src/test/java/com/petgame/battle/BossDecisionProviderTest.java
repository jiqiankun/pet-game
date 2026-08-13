package com.petgame.battle;

import com.petgame.battle.ai.BossDecisionProvider;
import com.petgame.battle.ai.DecisionProvider;
import com.petgame.battle.ai.WildEnemyDecisionProvider;
import com.petgame.battle.engine.BattleContext;
import com.petgame.battle.engine.BattleEngine;
import com.petgame.battle.model.BattleAction;
import com.petgame.battle.model.BattleUnit;
import com.petgame.battle.model.StatusInstance;
import com.petgame.config.GameConfigRegistry;
import com.petgame.config.model.SkillConfig;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.petgame.battle.BattleTestFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 阶段 7：BossDecisionProvider AI 决策测试（评分式规则 AI 改造后）。
 * <p>
 * 验证行为倾向与规则（固定种子 + 程序化配置夹具），覆盖：
 * 属性克制、低血目标、冷却过滤、高收益技能、三阶段策略、治疗策略、
 * 控制策略（抗性/衰减/不重复控制）、斩杀、fallback、死亡单位过滤、
 * 不依赖玩家等级动态缩放、与 WildEnemyDecisionProvider 的边界。
 */
class BossDecisionProviderTest {

    private final DecisionProvider waitProvider = (ctx, side) ->
            side.getActiveAliveUnits().stream()
                    .map(u -> BattleAction.skill(u.getUnitId(), "SKILL_WAIT", null))
                    .toList();

    // ---- 基础行为 ----

    @Test
    void decide_shouldReturnActionsForAllActiveAlive() {
        GameConfigRegistry registry = buildRegistry(0);
        BossDecisionProvider bossAI = new BossDecisionProvider(registry);

        BattleUnit boss = unit("BOSS", "FIRE", 100, 20, 10, 10, 10, 15, "SKILL_HIT");
        active(boss, 0);
        BattleUnit player = unit("P1", "WATER", 100, 10, 10, 5, 5, 10, "SKILL_WAIT");
        active(player, 0);

        BattleContext ctx = context(42, List.of(player), List.of(boss));
        List<BattleAction> actions = bossAI.decide(ctx, ctx.getEnemySide());
        assertNotNull(actions);
        assertFalse(actions.isEmpty(), "Boss AI 应返回至少一个行动");
        assertEquals("BOSS", actions.get(0).getPetId());
    }

    @Test
    void decide_shouldPreferLowHpTarget() {
        GameConfigRegistry registry = buildRegistry(0);
        BossDecisionProvider bossAI = new BossDecisionProvider(registry);

        BattleUnit boss = unit("BOSS", "FIRE", 100, 20, 10, 10, 10, 15, "SKILL_HIT");
        active(boss, 0);
        BattleUnit p1 = unit("P1", "WATER", 100, 10, 10, 5, 5, 10, "SKILL_WAIT");
        active(p1, 0);
        p1.setCurrentHp(90); // 高 HP
        BattleUnit p2 = unit("P2", "WATER", 100, 10, 10, 5, 5, 8, "SKILL_WAIT");
        active(p2, 1);
        p2.setCurrentHp(20); // 低 HP

        BattleContext ctx = context(42, List.of(p1, p2), List.of(boss));
        List<BattleAction> actions = bossAI.decide(ctx, ctx.getEnemySide());
        assertEquals("P2", actionOf(actions, "BOSS").getTargetId(), "Boss AI 应攻击低 HP 目标 P2");
    }

    @Test
    void decide_shouldUseAvailableSkill() {
        GameConfigRegistry registry = buildRegistry(0);
        BossDecisionProvider bossAI = new BossDecisionProvider(registry);

        BattleUnit boss = unit("BOSS", "FIRE", 100, 20, 10, 10, 10, 15, "SKILL_FIRE_HIT");
        active(boss, 0);
        BattleUnit player = unit("P1", "METAL", 100, 10, 10, 5, 5, 10, "SKILL_WAIT");
        active(player, 0);

        BattleContext ctx = context(42, List.of(player), List.of(boss));
        List<BattleAction> actions = bossAI.decide(ctx, ctx.getEnemySide());
        assertEquals("SKILL_FIRE_HIT", actionOf(actions, "BOSS").getSkillId(),
                "Boss AI 应使用可用的火属性技能");
    }

    @Test
    void decide_withMultipleBosses_shouldReturnActionForEach() {
        GameConfigRegistry registry = buildRegistry(0);
        BossDecisionProvider bossAI = new BossDecisionProvider(registry);

        BattleUnit boss1 = unit("BOSS1", "FIRE", 80, 15, 10, 10, 10, 20, "SKILL_HIT");
        active(boss1, 0);
        BattleUnit boss2 = unit("BOSS2", "METAL", 60, 12, 10, 10, 10, 15, "SKILL_HIT");
        active(boss2, 1);
        BattleUnit player = unit("P1", "WATER", 100, 10, 10, 5, 5, 10, "SKILL_WAIT");
        active(player, 0);

        BattleContext ctx = context(42, List.of(player), List.of(boss1, boss2));
        List<BattleAction> actions = bossAI.decide(ctx, ctx.getEnemySide());
        assertEquals(2, actions.size(), "两个 Boss 应各返回一个行动");
    }

    // ---- 属性克制与技能选择 ----

    @Test
    void decide_shouldPreferAdvantageElementSkill() {
        // 火克金属（夹具克制表），Boss 应选克制技能而不是同面板中性技能
        GameConfigRegistry registry = buildRegistry(0);
        BossDecisionProvider bossAI = new BossDecisionProvider(registry);

        BattleUnit boss = unit("BOSS", "METAL", 100, 20, 10, 10, 10, 15, "SKILL_HIT", "SKILL_FIRE_HIT");
        active(boss, 0);
        BattleUnit target = unit("P1", "METAL", 100, 10, 10, 5, 5, 10, "SKILL_WAIT");
        active(target, 0);

        BattleContext ctx = context(42, List.of(target), List.of(boss));
        List<BattleAction> actions = bossAI.decide(ctx, ctx.getEnemySide());
        assertEquals("SKILL_FIRE_HIT", actionOf(actions, "BOSS").getSkillId(),
                "Boss AI 应优先选择克制目标属性的技能");
    }

    @Test
    void decide_shouldNotSelectSkillOnCooldown() {
        GameConfigRegistry registry = buildRegistry(0);
        BossDecisionProvider bossAI = new BossDecisionProvider(registry);

        // SKILL_BIG 伤害最高但处于冷却，只能选 SKILL_HIT
        BattleUnit boss = unit("BOSS", "WATER", 100, 20, 10, 10, 10, 15, "SKILL_BIG", "SKILL_HIT");
        active(boss, 0);
        boss.getCooldowns().put("SKILL_BIG", 3);
        BattleUnit target = unit("P1", "WATER", 100, 10, 10, 5, 5, 10, "SKILL_WAIT");
        active(target, 0);

        BattleContext ctx = context(42, List.of(target), List.of(boss));
        List<BattleAction> actions = bossAI.decide(ctx, ctx.getEnemySide());
        assertEquals("SKILL_HIT", actionOf(actions, "BOSS").getSkillId(),
                "冷却中的技能不应被选择");
    }

    @Test
    void decide_shouldPreferHigherValueAttackSkill() {
        GameConfigRegistry registry = buildRegistry(0);
        BossDecisionProvider bossAI = new BossDecisionProvider(registry);

        BattleUnit boss = unit("BOSS", "WATER", 100, 20, 10, 10, 10, 15, "SKILL_HIT", "SKILL_BIG");
        active(boss, 0);
        BattleUnit target = unit("P1", "WATER", 10000, 10, 10, 5, 5, 10, "SKILL_WAIT");
        active(target, 0); // 高 HP 排除斩杀干扰

        BattleContext ctx = context(42, List.of(target), List.of(boss));
        List<BattleAction> actions = bossAI.decide(ctx, ctx.getEnemySide());
        assertEquals("SKILL_BIG", actionOf(actions, "BOSS").getSkillId(),
                "多个可用攻击技能时应倾向高收益技能");
    }

    // ---- 斩杀 ----

    @Test
    void decide_shouldPreferKillableTarget() {
        GameConfigRegistry registry = buildRegistry(0);
        BossDecisionProvider bossAI = new BossDecisionProvider(registry);

        BattleUnit boss = unit("BOSS", "WATER", 100, 20, 10, 10, 10, 15, "SKILL_HIT");
        active(boss, 0);
        // 两目标 HP% 相同（25%），P2 可被斩杀（估算伤害约 35 ≥ 25），P1 不可
        BattleUnit p1 = unit("P1", "WATER", 200, 10, 10, 5, 5, 10, "SKILL_WAIT");
        active(p1, 0);
        p1.setCurrentHp(50);
        BattleUnit p2 = unit("P2", "WATER", 100, 10, 10, 5, 5, 10, "SKILL_WAIT");
        active(p2, 1);
        p2.setCurrentHp(25);

        BattleContext ctx = context(42, List.of(p1, p2), List.of(boss));
        List<BattleAction> actions = bossAI.decide(ctx, ctx.getEnemySide());
        assertEquals("P2", actionOf(actions, "BOSS").getTargetId(),
                "存在可斩杀残血目标时应优先完成击杀");
    }

    // ---- 阶段策略 ----

    @Test
    void decide_phase1_shouldUseBalancedStrategy() {
        // 一阶段（未激活任何阶段触发器）：友方 55% HP 时治疗评分高于攻击 → 使用治疗
        GameConfigRegistry registry = buildRegistry(0);
        addSkill(registry, allyHealSkill());
        BossDecisionProvider bossAI = new BossDecisionProvider(registry);

        BattleUnit boss = unit("BOSS", "WATER", 100, 20, 10, 10, 10, 15, "SKILL_HIT", "SKILL_ALLY_HEAL");
        active(boss, 0);
        BattleUnit ally = unit("ALLY", "WATER", 100, 10, 10, 5, 5, 10, "SKILL_WAIT");
        active(ally, 1);
        ally.setCurrentHp(55);
        BattleUnit target = unit("P1", "WATER", 100, 10, 10, 5, 5, 10, "SKILL_WAIT");
        active(target, 0);

        BattleContext ctx = context(42, List.of(target), List.of(boss, ally));
        List<BattleAction> actions = bossAI.decide(ctx, ctx.getEnemySide());
        assertEquals("SKILL_ALLY_HEAL", actionOf(actions, "BOSS").getSkillId(),
                "一阶段应保持均衡：有治疗需求时使用治疗");
    }

    @Test
    void decide_phase2_shouldIncreaseAttackPriority() {
        // 与 phase1 完全相同的战场，仅激活 1 个阶段触发器 → 攻击权重提升并超过治疗
        GameConfigRegistry registry = buildRegistry(0);
        addSkill(registry, allyHealSkill());
        BossDecisionProvider bossAI = new BossDecisionProvider(registry);

        BattleUnit boss = unit("BOSS", "WATER", 100, 20, 10, 10, 10, 15, "SKILL_HIT", "SKILL_ALLY_HEAL");
        active(boss, 0);
        boss.getPhaseActivated().add(true); // 二阶段
        BattleUnit ally = unit("ALLY", "WATER", 100, 10, 10, 5, 5, 10, "SKILL_WAIT");
        active(ally, 1);
        ally.setCurrentHp(55);
        BattleUnit target = unit("P1", "WATER", 100, 10, 10, 5, 5, 10, "SKILL_WAIT");
        active(target, 0);

        BattleContext ctx = context(42, List.of(target), List.of(boss, ally));
        List<BattleAction> actions = bossAI.decide(ctx, ctx.getEnemySide());
        assertEquals("SKILL_HIT", actionOf(actions, "BOSS").getSkillId(),
                "二阶段应明显提高攻击技能优先级");
    }

    @Test
    void decide_phase3_shouldPreferBurstOverHeal() {
        // 激活 2 个阶段触发器（三阶段）：即使友方低血，爆发攻击也优先于小额治疗
        GameConfigRegistry registry = buildRegistry(0);
        addSkill(registry, allyHealSkill());
        BossDecisionProvider bossAI = new BossDecisionProvider(registry);

        BattleUnit boss = unit("BOSS", "WATER", 100, 20, 10, 10, 10, 15, "SKILL_HIT", "SKILL_ALLY_HEAL");
        active(boss, 0);
        boss.getPhaseActivated().add(true);
        boss.getPhaseActivated().add(true); // 三阶段
        BattleUnit ally = unit("ALLY", "WATER", 100, 10, 10, 5, 5, 10, "SKILL_WAIT");
        active(ally, 1);
        ally.setCurrentHp(55);
        BattleUnit target = unit("P1", "WATER", 100, 10, 10, 5, 5, 10, "SKILL_WAIT");
        active(target, 0);

        BattleContext ctx = context(42, List.of(target), List.of(boss, ally));
        List<BattleAction> actions = bossAI.decide(ctx, ctx.getEnemySide());
        assertEquals("SKILL_HIT", actionOf(actions, "BOSS").getSkillId(),
                "三阶段应倾向爆发输出而非小额治疗");
    }

    @Test
    void decide_phaseTransition_shouldChangeStrategy() {
        // 同一战场状态：一阶段治疗、三阶段攻击，验证阶段切换后决策策略正确变化
        GameConfigRegistry registry = buildRegistry(0);
        addSkill(registry, allyHealSkill());
        BossDecisionProvider bossAI = new BossDecisionProvider(registry);

        // 一阶段
        BattleUnit boss1 = unit("BOSS", "WATER", 100, 20, 10, 10, 10, 15, "SKILL_HIT", "SKILL_ALLY_HEAL");
        active(boss1, 0);
        BattleUnit ally1 = unit("ALLY", "WATER", 100, 10, 10, 5, 5, 10, "SKILL_WAIT");
        active(ally1, 1);
        ally1.setCurrentHp(55);
        BattleUnit target1 = unit("P1", "WATER", 100, 10, 10, 5, 5, 10, "SKILL_WAIT");
        active(target1, 0);
        BattleContext ctx1 = context(42, List.of(target1), List.of(boss1, ally1));

        // 三阶段（同战场数值）
        BattleUnit boss3 = unit("BOSS", "WATER", 100, 20, 10, 10, 10, 15, "SKILL_HIT", "SKILL_ALLY_HEAL");
        active(boss3, 0);
        boss3.getPhaseActivated().add(true);
        boss3.getPhaseActivated().add(true);
        BattleUnit ally3 = unit("ALLY", "WATER", 100, 10, 10, 5, 5, 10, "SKILL_WAIT");
        active(ally3, 1);
        ally3.setCurrentHp(55);
        BattleUnit target3 = unit("P1", "WATER", 100, 10, 10, 5, 5, 10, "SKILL_WAIT");
        active(target3, 0);
        BattleContext ctx3 = context(43, List.of(target3), List.of(boss3, ally3));

        assertEquals("SKILL_ALLY_HEAL", actionOf(bossAI.decide(ctx1, ctx1.getEnemySide()), "BOSS").getSkillId());
        assertEquals("SKILL_HIT", actionOf(bossAI.decide(ctx3, ctx3.getEnemySide()), "BOSS").getSkillId());
    }

    // ---- 治疗策略 ----

    @Test
    void decide_shouldHealWhenAllyLowHp() {
        // 友方 HP < 40% 时治疗优先级明显提高（紧迫度倍率）
        GameConfigRegistry registry = buildRegistry(0);
        addSkill(registry, allyHealSkill());
        BossDecisionProvider bossAI = new BossDecisionProvider(registry);

        BattleUnit boss = unit("BOSS", "WATER", 100, 20, 10, 10, 10, 15, "SKILL_HIT", "SKILL_ALLY_HEAL");
        active(boss, 0);
        BattleUnit ally = unit("ALLY", "WATER", 100, 10, 10, 5, 5, 10, "SKILL_WAIT");
        active(ally, 1);
        ally.setCurrentHp(30); // 30% < 40% 触发阈值
        BattleUnit target = unit("P1", "WATER", 100, 10, 10, 5, 5, 10, "SKILL_WAIT");
        active(target, 0);

        BattleContext ctx = context(42, List.of(target), List.of(boss, ally));
        BattleAction action = actionOf(bossAI.decide(ctx, ctx.getEnemySide()), "BOSS");
        assertEquals("SKILL_ALLY_HEAL", action.getSkillId(), "友方低于治疗阈值时应优先治疗");
        assertEquals("ALLY", action.getTargetId(), "应治疗 HP 百分比最低的友方");
    }

    @Test
    void decide_shouldNotHealWhenAlliesFullHp() {
        // 全员满血时不产生无意义治疗
        GameConfigRegistry registry = buildRegistry(0);
        addSkill(registry, allyHealSkill());
        BossDecisionProvider bossAI = new BossDecisionProvider(registry);

        BattleUnit boss = unit("BOSS", "WATER", 100, 20, 10, 10, 10, 15, "SKILL_HIT", "SKILL_ALLY_HEAL");
        active(boss, 0);
        BattleUnit ally = unit("ALLY", "WATER", 100, 10, 10, 5, 5, 10, "SKILL_WAIT");
        active(ally, 1);
        BattleUnit target = unit("P1", "WATER", 100, 10, 10, 5, 5, 10, "SKILL_WAIT");
        active(target, 0);

        BattleContext ctx = context(42, List.of(target), List.of(boss, ally));
        assertEquals("SKILL_HIT", actionOf(bossAI.decide(ctx, ctx.getEnemySide()), "BOSS").getSkillId(),
                "满血时不应无意义治疗");
    }

    // ---- 控制策略 ----

    @Test
    void decide_shouldUseControlOnUncontrolledTarget() {
        // 一阶段、目标未受控、无衰减：控制技能（基础分 60）优于普通攻击（约 35）
        GameConfigRegistry registry = buildRegistry(0);
        addSkill(registry, controlSkill());
        BossDecisionProvider bossAI = new BossDecisionProvider(registry);

        BattleUnit boss = unit("BOSS", "WATER", 100, 20, 10, 10, 10, 15, "SKILL_HIT", "SKILL_CONTROL");
        active(boss, 0);
        BattleUnit target = unit("P1", "WATER", 100, 10, 10, 5, 5, 10, "SKILL_WAIT");
        active(target, 0);

        BattleContext ctx = context(42, List.of(target), List.of(boss));
        assertEquals("SKILL_CONTROL", actionOf(bossAI.decide(ctx, ctx.getEnemySide()), "BOSS").getSkillId(),
                "目标未受控时应合理使用控制技能");
    }

    @Test
    void decide_shouldNotRepeatControlOnControlledTarget() {
        // 目标已处于控制状态（SILENCE）→ 控制候选大幅降权，转为攻击
        GameConfigRegistry registry = buildRegistry(0);
        addSkill(registry, controlSkill());
        BossDecisionProvider bossAI = new BossDecisionProvider(registry);

        BattleUnit boss = unit("BOSS", "WATER", 100, 20, 10, 10, 10, 15, "SKILL_HIT", "SKILL_CONTROL");
        active(boss, 0);
        BattleUnit target = unit("P1", "WATER", 100, 10, 10, 5, 5, 10, "SKILL_WAIT");
        active(target, 0);
        target.getStatuses().add(new StatusInstance("SILENCE", 2, "BOSS"));

        BattleContext ctx = context(42, List.of(target), List.of(boss));
        assertEquals("SKILL_HIT", actionOf(bossAI.decide(ctx, ctx.getEnemySide()), "BOSS").getSkillId(),
                "已被控制目标不应被持续重复施加控制");
    }

    @Test
    void decide_shouldConsiderControlResistance() {
        // 控制抗性低的目标成功率低 → 优先控制无抗性目标
        GameConfigRegistry registry = buildRegistry(0);
        addSkill(registry, controlSkill());
        BossDecisionProvider bossAI = new BossDecisionProvider(registry);

        BattleUnit boss = unit("BOSS", "WATER", 100, 20, 10, 10, 10, 15, "SKILL_CONTROL");
        active(boss, 0);
        BattleUnit p1 = unit("P1", "WATER", 100, 10, 10, 5, 5, 10, "SKILL_WAIT");
        active(p1, 0);
        p1.setControlResistance(1.0);
        BattleUnit p2 = unit("P2", "WATER", 100, 10, 10, 5, 5, 10, "SKILL_WAIT");
        active(p2, 1);
        p2.setControlResistance(0.3); // 高抗性 → 估算成功率低

        BattleContext ctx = context(42, List.of(p1, p2), List.of(boss));
        assertEquals("P1", actionOf(bossAI.decide(ctx, ctx.getEnemySide()), "BOSS").getTargetId(),
                "控制抗性应影响控制目标选择");
    }

    @Test
    void decide_shouldConsiderConsecutiveControlDecay() {
        // 连续控制衰减：已被控制 2 次的目标（衰减 ×0.4）评分低于未受控目标
        GameConfigRegistry registry = buildRegistry(0);
        addSkill(registry, controlSkill());
        registry.getSystemRules().setConsecutiveControlDecay(List.of(1.0, 0.7, 0.4));
        BossDecisionProvider bossAI = new BossDecisionProvider(registry);

        BattleUnit boss = unit("BOSS", "WATER", 100, 20, 10, 10, 10, 15, "SKILL_CONTROL");
        active(boss, 0);
        BattleUnit p1 = unit("P1", "WATER", 100, 10, 10, 5, 5, 10, "SKILL_WAIT");
        active(p1, 0);
        p1.setConsecutiveControlCount(0);
        BattleUnit p2 = unit("P2", "WATER", 100, 10, 10, 5, 5, 10, "SKILL_WAIT");
        active(p2, 1);
        p2.setConsecutiveControlCount(2); // 第 3 次控制成功率 ×0.4

        BattleContext ctx = context(42, List.of(p1, p2), List.of(boss));
        assertEquals("P1", actionOf(bossAI.decide(ctx, ctx.getEnemySide()), "BOSS").getTargetId(),
                "连续控制衰减应降低重复控制同一目标的评分");
    }

    // ---- 边界情况 ----

    @Test
    void decide_allSkillsOnCooldown_shouldDefend() {
        GameConfigRegistry registry = buildRegistry(0);
        BossDecisionProvider bossAI = new BossDecisionProvider(registry);

        BattleUnit boss = unit("BOSS", "WATER", 100, 20, 10, 10, 10, 15, "SKILL_BIG", "SKILL_CD");
        active(boss, 0);
        boss.getCooldowns().put("SKILL_BIG", 2);
        boss.getCooldowns().put("SKILL_CD", 3);
        BattleUnit target = unit("P1", "WATER", 100, 10, 10, 5, 5, 10, "SKILL_WAIT");
        active(target, 0);

        BattleContext ctx = context(42, List.of(target), List.of(boss));
        BattleAction action = actionOf(bossAI.decide(ctx, ctx.getEnemySide()), "BOSS");
        assertEquals("DEFEND", action.getType(), "所有技能冷却时应 fallback 为防御");
    }

    @Test
    void decide_silenced_shouldDefend() {
        GameConfigRegistry registry = buildRegistry(0);
        BossDecisionProvider bossAI = new BossDecisionProvider(registry);

        BattleUnit boss = unit("BOSS", "WATER", 100, 20, 10, 10, 10, 15, "SKILL_HIT");
        active(boss, 0);
        boss.getStatuses().add(new StatusInstance("SILENCE", 2, "P1"));
        BattleUnit target = unit("P1", "WATER", 100, 10, 10, 5, 5, 10, "SKILL_WAIT");
        active(target, 0);

        BattleContext ctx = context(42, List.of(target), List.of(boss));
        assertEquals("DEFEND", actionOf(bossAI.decide(ctx, ctx.getEnemySide()), "BOSS").getType(),
                "沉默时应 fallback 为防御");
    }

    @Test
    void decide_shouldFilterDeadUnits() {
        GameConfigRegistry registry = buildRegistry(0);
        BossDecisionProvider bossAI = new BossDecisionProvider(registry);

        BattleUnit boss1 = unit("BOSS1", "WATER", 100, 20, 10, 10, 10, 15, "SKILL_HIT");
        active(boss1, 0);
        BattleUnit boss2 = unit("BOSS2", "WATER", 100, 20, 10, 10, 10, 15, "SKILL_HIT");
        active(boss2, 1);
        boss2.setAlive(false); // 死亡 Boss 不产生行动
        boss2.setActive(false);

        BattleUnit p1 = unit("P1", "WATER", 100, 10, 10, 5, 5, 10, "SKILL_WAIT");
        active(p1, 0);
        BattleUnit p2 = unit("P2", "WATER", 100, 10, 10, 5, 5, 10, "SKILL_WAIT");
        active(p2, 1);
        p2.setAlive(false); // 死亡目标不会成为攻击目标
        p2.setActive(false);

        BattleContext ctx = context(42, List.of(p1, p2), List.of(boss1, boss2));
        List<BattleAction> actions = bossAI.decide(ctx, ctx.getEnemySide());
        assertEquals(1, actions.size(), "死亡 Boss 不产生行动");
        assertEquals("P1", actions.get(0).getTargetId(), "死亡单位不会成为攻击目标");
    }

    // ---- 与玩家强度解耦 / 与野怪 AI 的边界 ----

    @Test
    void decide_shouldNotDependOnPlayerLevel() {
        // 相同战场状态、仅目标等级不同 → 决策一致（不读取玩家等级做动态缩放）
        GameConfigRegistry registry = buildRegistry(0);
        BossDecisionProvider bossAI = new BossDecisionProvider(registry);

        BattleAction low = decideAgainstTargetLevel(bossAI, registry, 5);
        BattleAction high = decideAgainstTargetLevel(bossAI, registry, 50);
        assertEquals(low.getSkillId(), high.getSkillId(), "决策不应随玩家等级变化");
        assertEquals(low.getTargetId(), high.getTargetId(), "决策不应随玩家等级变化");
    }

    @Test
    void wildEnemyProvider_shouldRemainUnchanged() {
        // 普通敌人仍使用 WildEnemyDecisionProvider（随机选择就绪技能），不受 Boss AI 影响
        GameConfigRegistry registry = buildRegistry(0);
        WildEnemyDecisionProvider wildAI = new WildEnemyDecisionProvider(registry);

        BattleUnit wild = unit("WILD", "WATER", 100, 20, 10, 10, 10, 15, "SKILL_HIT", "SKILL_FIRE_HIT");
        active(wild, 0);
        BattleUnit target = unit("P1", "WATER", 20, 10, 10, 5, 5, 10, "SKILL_WAIT");
        active(target, 0);

        BattleContext ctx = context(42, List.of(target), List.of(wild));
        BattleAction action = wildAI.decide(ctx, ctx.getEnemySide()).get(0);
        assertEquals("SKILL", action.getType());
        assertTrue(List.of("SKILL_HIT", "SKILL_FIRE_HIT").contains(action.getSkillId()),
                "野怪 AI 仍从就绪技能中选择");
    }

    // ---- 辅助 ----

    /** 从行动列表中取出指定单位的行动。 */
    private BattleAction actionOf(List<BattleAction> actions, String petId) {
        return actions.stream().filter(a -> petId.equals(a.getPetId())).findFirst()
                .orElseThrow(() -> new AssertionError("未找到单位 " + petId + " 的行动"));
    }

    /** 友方单体治疗技能（WATER，base 30 + spirit 系数）。 */
    private SkillConfig allyHealSkill() {
        SkillConfig skill = pubSkill("SKILL_ALLY_HEAL", "WATER", "HEAL", "ALLY_SINGLE", 30,
                Map.of("SPIRIT", 1.0));
        skill.setDamageType("NONE");
        return skill;
    }

    /** 控制技能（必中沉默，SPECIAL_CONTROL）。 */
    private SkillConfig controlSkill() {
        SkillConfig skill = pubSkill("SKILL_CONTROL", "NONE", "NONE", "ENEMY_SINGLE", 0, Map.of());
        skill.setDamageType("NONE");
        SkillConfig.SkillEffectConfig effect = new SkillConfig.SkillEffectConfig();
        effect.setType("APPLY_STATUS");
        effect.setStatusId("SILENCE");
        effect.setChance(1.0);
        skill.getEffects().add(effect);
        return skill;
    }

    /** 以指定目标等级运行一次决策（其余战场状态固定）。 */
    private BattleAction decideAgainstTargetLevel(BossDecisionProvider bossAI,
                                                  GameConfigRegistry registry, int targetLevel) {
        BattleUnit boss = unit("BOSS", "WATER", 100, 20, 10, 10, 10, 15, "SKILL_HIT", "SKILL_BIG");
        active(boss, 0);
        BattleUnit target = unit("P1", "WATER", 100, 10, 10, 5, 5, 10, "SKILL_WAIT");
        active(target, 0);
        target.setLevel(targetLevel);
        BattleContext ctx = context(42, List.of(target), List.of(boss));
        return actionOf(bossAI.decide(ctx, ctx.getEnemySide()), "BOSS");
    }
}
