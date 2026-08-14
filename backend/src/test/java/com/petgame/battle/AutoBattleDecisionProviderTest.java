package com.petgame.battle;

import com.petgame.battle.ai.AutoBattleDecisionProvider;
import com.petgame.battle.ai.AutoBattleSettings;
import com.petgame.battle.engine.BattleContext;
import com.petgame.battle.model.BattleAction;
import com.petgame.battle.model.BattleSide;
import com.petgame.battle.model.BattleUnit;
import com.petgame.battle.model.StatusInstance;
import com.petgame.config.GameConfigRegistry;
import com.petgame.config.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AutoBattleDecisionProvider 单元测试（阶段 10 自动战斗策略系统）。
 * <p>
 * 覆盖：基础决策合法性、四套策略差异、治疗/控制/驱散/破盾/吸血/终结、
 * 命运天平收益模型、捕捉策略（误杀风险/留生一击/1HP+震慑）、自动换宠、
 * 自动恢复道具/复苏（默认关闭）、fallback。
 */
class AutoBattleDecisionProviderTest {

    private GameConfigRegistry registry;
    private AutoBattleDecisionProvider provider;

    @BeforeEach
    void setUp() {
        registry = buildRegistry();
        provider = new AutoBattleDecisionProvider(registry);
    }

    // ==================== 基础决策 ====================

    @Test
    void decide_onlyLegalSkillChosen() {
        BattleUnit player = unit("P_1", 100, 20, "SKILL_HIT");
        BattleUnit enemy = unit("E_1", 100, 20, "SKILL_HIT");
        BattleContext ctx = ctx(1L, List.of(active(player)), List.of(active(enemy)), "BALANCED");

        BattleAction action = decide(ctx);

        assertEquals("SKILL", action.getType());
        assertEquals("SKILL_HIT", action.getSkillId());
        assertEquals("E_1", action.getTargetId());
    }

    @Test
    void decide_cooldownSkillNotChosen_fallbackDefend() {
        BattleUnit player = unit("P_1", 100, 20, "SKILL_HIT");
        player.getCooldowns().put("SKILL_HIT", 2); // 唯一技能冷却中
        BattleUnit enemy = unit("E_1", 100, 20, "SKILL_HIT");
        BattleContext ctx = ctx(1L, List.of(active(player)), List.of(active(enemy)), "BALANCED");

        BattleAction action = decide(ctx);

        assertEquals("DEFEND", action.getType());
    }

    @Test
    void decide_deadTargetNotAttacked() {
        BattleUnit player = unit("P_1", 100, 20, "SKILL_HIT");
        BattleUnit alive = unit("E_1", 100, 20, "SKILL_HIT");
        BattleUnit dead = unit("E_2", 100, 20, "SKILL_HIT");
        dead.setAlive(false);
        dead.setActive(false);
        BattleContext ctx = ctx(1L, List.of(active(player)), List.of(active(alive), dead), "BALANCED");

        BattleAction action = decide(ctx);

        assertEquals("SKILL", action.getType());
        assertEquals("E_1", action.getTargetId());
    }

    @Test
    void decide_elementAdvantagePreferred() {
        // WATER 克制 FIRE：同等基础值下优先选择克制技能
        BattleUnit player = unit("P_1", 100, 20, "SKILL_WATER_HIT", "SKILL_NEUTRAL");
        BattleUnit enemy = unit("E_1", 100, 20, "SKILL_HIT");
        enemy.setElement("FIRE");
        BattleContext ctx = ctx(1L, List.of(active(player)), List.of(active(enemy)), "BALANCED");

        BattleAction action = decide(ctx);

        assertEquals("SKILL_WATER_HIT", action.getSkillId());
    }

    @Test
    void decide_killOpportunityTaken() {
        // 敌人残血可斩杀时选择攻击（而不是无意义防御）
        BattleUnit player = unit("P_1", 100, 20, "SKILL_HIT");
        BattleUnit enemy = unit("E_1", 100, 20, "SKILL_HIT");
        enemy.setCurrentHp(5);
        BattleContext ctx = ctx(1L, List.of(active(player)), List.of(active(enemy)), "BALANCED");

        BattleAction action = decide(ctx);

        assertEquals("SKILL", action.getType());
        assertEquals("E_1", action.getTargetId());
    }

    @Test
    void decide_silencedUnitFallsBackToDefend() {
        BattleUnit player = unit("P_1", 100, 20, "SKILL_HIT");
        player.getStatuses().add(new StatusInstance("SILENCE", 2, "E_1"));
        BattleUnit enemy = unit("E_1", 100, 20, "SKILL_HIT");
        BattleContext ctx = ctx(1L, List.of(active(player)), List.of(active(enemy)), "BALANCED");

        BattleAction action = decide(ctx);

        assertEquals("DEFEND", action.getType());
    }

    // ==================== 治疗 ====================

    @Test
    void heal_fullHpAllies_noPointlessHeal() {
        BattleUnit player = unit("P_1", 100, 20, "SKILL_HEAL_SELF", "SKILL_HIT");
        BattleUnit enemy = unit("E_1", 100, 20, "SKILL_HIT");
        BattleContext ctx = ctx(1L, List.of(active(player)), List.of(active(enemy)), "BALANCED");

        BattleAction action = decide(ctx);

        assertEquals("SKILL_HIT", action.getSkillId(), "满血不应无意义治疗");
    }

    @Test
    void heal_lowHp_increasesHealPriority() {
        BattleUnit player = unit("P_1", 100, 20, "SKILL_HEAL_SELF", "SKILL_SMALL");
        player.setCurrentHp(25); // 25% HP → 高紧迫度
        BattleUnit enemy = unit("E_1", 100, 20, "SKILL_HIT");
        BattleContext ctx = ctx(1L, List.of(active(player)), List.of(active(enemy)), "BALANCED");

        BattleAction action = decide(ctx);

        assertEquals("SKILL_HEAL_SELF", action.getSkillId(), "低血量应优先治疗");
    }

    @Test
    void heal_allyTarget_mostDamagedAllyChosen() {
        BattleUnit healer = unit("P_1", 100, 20, "SKILL_HEAL_ALLY");
        BattleUnit hurt = unit("P_2", 100, 20, "SKILL_HIT");
        hurt.setCurrentHp(30);
        BattleUnit healthy = unit("P_3", 100, 20, "SKILL_HIT");
        BattleUnit enemy = unit("E_1", 100, 20, "SKILL_HIT");
        BattleContext ctx = ctx(1L, List.of(active(healer), active(hurt), active(healthy)),
                List.of(active(enemy)), "BALANCED");

        BattleAction action = decide(ctx);

        assertEquals("SKILL_HEAL_ALLY", action.getSkillId());
        assertEquals("P_2", action.getTargetId(), "应治疗缺血最严重的队友");
    }

    // ==================== 控制 ====================

    @Test
    void control_alreadyControlledTarget_avoidRepeat() {
        // 两个敌人：E_1 已被控制，E_2 未被控制 → 控制技能指向 E_2
        BattleUnit player = unit("P_1", 100, 20, "SKILL_CONTROL");
        BattleUnit e1 = unit("E_1", 100, 20, "SKILL_HIT");
        e1.getStatuses().add(new StatusInstance("CONFUSE", 2, "P_1"));
        BattleUnit e2 = unit("E_2", 100, 20, "SKILL_HIT");
        BattleContext ctx = ctx(1L, List.of(active(player)), List.of(active(e1), active(e2)), "BALANCED");

        BattleAction action = decide(ctx);

        assertEquals("SKILL_CONTROL", action.getSkillId());
        assertEquals("E_2", action.getTargetId(), "不应机械连续控制已受控目标");
    }

    @Test
    void control_singleControlledTarget_scoreDropsButStillLegal() {
        BattleUnit player = unit("P_1", 100, 20, "SKILL_CONTROL");
        BattleUnit e1 = unit("E_1", 100, 20, "SKILL_HIT");
        e1.getStatuses().add(new StatusInstance("CONFUSE", 2, "P_1"));
        BattleContext ctx = ctx(1L, List.of(active(player)), List.of(active(e1)), "BALANCED");

        BattleAction action = decide(ctx);

        // 唯一目标已受控时控制分大幅下降，但仍应产出合法行动（控制或防御）
        assertNotNull(action.getType());
    }

    // ==================== 驱散 ====================

    @Test
    void dispel_nothingToDispel_lowValue() {
        // 敌方无 BUFF：驱散价值极低，攻击胜出
        BattleUnit player = unit("P_1", 100, 20, "SKILL_DISPEL", "SKILL_HIT");
        BattleUnit enemy = unit("E_1", 100, 20, "SKILL_HIT");
        BattleContext ctx = ctx(1L, List.of(active(player)), List.of(active(enemy)), "BALANCED");

        BattleAction action = decide(ctx);

        assertEquals("SKILL_HIT", action.getSkillId(), "无可驱散状态时不应浪费驱散");
    }

    @Test
    void dispel_enemyHasBuffs_valueRises() {
        // 敌方携带 BUFF：驱散价值上升并胜过弱攻击
        BattleUnit player = unit("P_1", 100, 20, "SKILL_DISPEL", "SKILL_TINY");
        BattleUnit enemy = unit("E_1", 100, 20, "SKILL_HIT");
        enemy.getStatuses().add(new StatusInstance("ATK_UP", 2, "E_1"));
        enemy.getStatuses().add(new StatusInstance("DEF_UP", 2, "E_1"));
        BattleContext ctx = ctx(1L, List.of(active(player)), List.of(active(enemy)), "BALANCED");

        BattleAction action = decide(ctx);

        assertEquals("SKILL_DISPEL", action.getSkillId(), "有高价值增益时应驱散");
    }

    // ==================== 破盾 ====================

    @Test
    void shieldBreak_targetHasShield_preferred() {
        BattleUnit player = unit("P_1", 100, 20, "SKILL_BREAKER", "SKILL_HIT");
        BattleUnit enemy = unit("E_1", 100, 20, "SKILL_HIT");
        enemy.setShield(80);
        BattleContext ctx = ctx(1L, List.of(active(player)), List.of(active(enemy)), "BALANCED");

        BattleAction action = decide(ctx);

        assertEquals("SKILL_BREAKER", action.getSkillId(), "目标有护盾时破盾技能应提权");
    }

    @Test
    void shieldBreak_noShield_notSpammed() {
        BattleUnit player = unit("P_1", 100, 20, "SKILL_BREAKER", "SKILL_HIT");
        BattleUnit enemy = unit("E_1", 100, 20, "SKILL_HIT");
        BattleContext ctx = ctx(1L, List.of(active(player)), List.of(active(enemy)), "BALANCED");

        BattleAction action = decide(ctx);

        assertEquals("SKILL_HIT", action.getSkillId(), "无护盾时不应因标签释放破盾");
    }

    // ==================== 吸血 ====================

    @Test
    void lifeSteal_selfLowHp_valueIncreases() {
        BattleUnit player = unit("P_1", 100, 20, "SKILL_DRAIN", "SKILL_HIT");
        player.setCurrentHp(30);
        BattleUnit enemy = unit("E_1", 100, 20, "SKILL_HIT");
        BattleContext ctx = ctx(1L, List.of(active(player)), List.of(active(enemy)), "BALANCED");

        BattleAction action = decide(ctx);

        assertEquals("SKILL_DRAIN", action.getSkillId(), "自身残血时吸血技能价值应增加");
    }

    @Test
    void lifeSteal_selfFullHp_noUnreasonableBonus() {
        BattleUnit player = unit("P_1", 100, 20, "SKILL_DRAIN", "SKILL_PLAIN30");
        BattleUnit enemy = unit("E_1", 100, 20, "SKILL_HIT");
        BattleContext ctx = ctx(1L, List.of(active(player)), List.of(active(enemy)), "BALANCED");

        BattleAction action = decide(ctx);

        // 满血时吸血无恢复加成，基础值更低的吸血技能不应胜过普通攻击
        assertEquals("SKILL_PLAIN30", action.getSkillId(), "满血时吸血不应获得不合理高分");
    }

    // ==================== FINISHER ====================

    @Test
    void finisher_targetLowHp_preferred() {
        BattleUnit player = unit("P_1", 100, 20, "SKILL_FINISH", "SKILL_PLAIN30");
        BattleUnit enemy = unit("E_1", 100, 20, "SKILL_HIT");
        enemy.setCurrentHp(25); // 25% ≤ 30% 阈值
        BattleContext ctx = ctx(1L, List.of(active(player)), List.of(active(enemy)), "BALANCED");

        BattleAction action = decide(ctx);

        assertEquals("SKILL_FINISH", action.getSkillId(), "目标低血时终结技能应提权");
    }

    @Test
    void finisher_targetFullHp_notPreferred() {
        // 满血目标：终结技能无加成，基础值更高的大攻击胜出
        BattleUnit player = unit("P_1", 100, 20, "SKILL_FINISH", "SKILL_BIG40");
        BattleUnit enemy = unit("E_1", 100, 20, "SKILL_HIT");
        BattleContext ctx = ctx(1L, List.of(active(player)), List.of(active(enemy)), "BALANCED");

        BattleAction action = decide(ctx);

        assertEquals("SKILL_BIG40", action.getSkillId(), "满血目标时终结技能不应优先");
    }

    // ==================== 命运天平（HP_PERCENT_EXCHANGE） ====================

    @Test
    void hpExchange_smallBenefit_notUsed() {
        // 自己 60% vs 目标 70%：净收益 0.2 < 阈值 0.25，不使用交换
        BattleUnit player = unit("P_1", 100, 20, "SKILL_BALANCE", "SKILL_TINY");
        player.setCurrentHp(60);
        BattleUnit enemy = unit("E_1", 100, 20, "SKILL_HIT");
        enemy.setCurrentHp(70);
        BattleContext ctx = ctx(1L, List.of(active(player)), List.of(active(enemy)), "BALANCED");

        BattleAction action = decide(ctx);

        assertEquals("SKILL_TINY", action.getSkillId(), "小收益交换不应触发");
    }

    @Test
    void hpExchange_largeBenefit_used() {
        // 自己 20% vs 目标 80%：净收益 1.2 ≥ 阈值，交换有显著价值
        BattleUnit player = unit("P_1", 100, 20, "SKILL_BALANCE", "SKILL_TINY");
        player.setCurrentHp(20);
        BattleUnit enemy = unit("E_1", 100, 20, "SKILL_HIT");
        enemy.setCurrentHp(80);
        BattleContext ctx = ctx(1L, List.of(active(player)), List.of(active(enemy)), "BALANCED");

        BattleAction action = decide(ctx);

        assertEquals("SKILL_BALANCE", action.getSkillId(), "明显净收益时应使用 HP 交换");
    }

    @Test
    void hpExchange_bossTarget_higherThreshold() {
        // 自己 40% vs 目标 60%：净收益 0.4；普通阈值 0.25 通过，Boss 阈值 0.45 不通过
        BattleUnit player = unit("P_1", 100, 20, "SKILL_BALANCE", "SKILL_TINY");
        player.setCurrentHp(40);
        BattleUnit enemy = unit("E_1", 100, 20, "SKILL_HIT");
        enemy.setCurrentHp(60);

        BattleContext normal = ctx(1L, List.of(active(player)), List.of(active(enemy)), "BALANCED");
        assertEquals("SKILL_BALANCE", decide(normal).getSkillId(), "普通战斗阈值较低应使用");

        BattleUnit player2 = unit("P_1", 100, 20, "SKILL_BALANCE", "SKILL_TINY");
        player2.setCurrentHp(40);
        BattleUnit enemy2 = unit("E_1", 100, 20, "SKILL_HIT");
        enemy2.setCurrentHp(60);
        BattleContext boss = ctx(1L, List.of(active(player2)), List.of(active(enemy2)), "BALANCED");
        boss.setBattleType("BOSS");
        boss.setUncapturable(true);
        assertEquals("SKILL_TINY", decide(boss).getSkillId(), "Boss 目标需要更高收益阈值");
    }

    // ==================== 捕捉策略 ====================

    @Test
    void capture_highHpTarget_normalWhittling() {
        // CAPTURE 策略目标高血时正常削血（攻击行动）
        BattleUnit player = unit("P_1", 100, 20, "SKILL_HIT");
        BattleUnit wild = wildUnit("E_1", 100, 20);
        BattleContext ctx = ctx(1L, List.of(active(player)), List.of(active(wild)), "CAPTURE");

        BattleAction action = decide(ctx);

        assertEquals("SKILL", action.getType(), "目标高血时应正常削血");
    }

    @Test
    void capture_lowHpTarget_avoidKillRisk() {
        // CAPTURE 策略目标进入危险血量区：大伤害技能有击杀风险被降权，小伤害技能胜出
        BattleUnit player = unit("P_1", 100, 20, "SKILL_BIG1000", "SKILL_TINY");
        BattleUnit wild = wildUnit("E_1", 100, 20);
        wild.setCurrentHp(30); // 30% < 40% 危险区，SKILL_BIG1000 必定击杀
        BattleContext ctx = ctx(1L, List.of(active(player)), List.of(active(wild)), "CAPTURE");
        ctx.getAvailableCaptureBalls().put("ITEM_BALL_NORMAL", 5);

        BattleAction action = decide(ctx);

        assertEquals("SKILL_TINY", action.getSkillId(), "低血量目标应避免明显误杀技能");
    }

    @Test
    void capture_leaveAliveSkill_preferredInDangerZone() {
        // 危险血量区：留生一击（LEAVE_AT_ONE_HP）可安全压至 1HP，优先于小伤害
        BattleUnit player = unit("P_1", 100, 20, "SKILL_LEAVE_ALIVE", "SKILL_TINY");
        BattleUnit wild = wildUnit("E_1", 100, 20);
        wild.setCurrentHp(35);
        BattleContext ctx = ctx(1L, List.of(active(player)), List.of(active(wild)), "CAPTURE");
        ctx.getAvailableCaptureBalls().put("ITEM_BALL_NORMAL", 5);

        BattleAction action = decide(ctx);

        assertEquals("SKILL_LEAVE_ALIVE", action.getSkillId(), "存在留生一击时应优先安全压血");
    }

    @Test
    void capture_oneHpWithStun_captureNow() {
        // 目标 1 HP + 震慑：优先捕捉而不是继续攻击
        BattleUnit player = unit("P_1", 100, 20, "SKILL_HIT");
        BattleUnit wild = wildUnit("E_1", 100, 20);
        wild.setCurrentHp(1);
        wild.getStatuses().add(new StatusInstance("CAPTURE_STUN", 1, "P_1"));
        BattleContext ctx = ctx(1L, List.of(active(player)), List.of(active(wild)), "CAPTURE");
        ctx.getAvailableCaptureBalls().put("ITEM_BALL_NORMAL", 5);

        BattleAction action = decide(ctx);

        assertEquals("CAPTURE", action.getType(), "1 HP + 震慑时应优先捕捉");
        assertEquals("E_1", action.getTargetId());
    }

    @Test
    void capture_noBalls_noCaptureCandidate() {
        // 没有捕捉球时不生成捕捉候选，继续攻击
        BattleUnit player = unit("P_1", 100, 20, "SKILL_HIT");
        BattleUnit wild = wildUnit("E_1", 100, 20);
        wild.setCurrentHp(1);
        wild.getStatuses().add(new StatusInstance("CAPTURE_STUN", 1, "P_1"));
        BattleContext ctx = ctx(1L, List.of(active(player)), List.of(active(wild)), "CAPTURE");

        BattleAction action = decide(ctx);

        assertEquals("SKILL", action.getType());
    }

    @Test
    void capture_bossBattle_uncapturable() {
        // Boss 战（uncapturable）不生成捕捉候选
        BattleUnit player = unit("P_1", 100, 20, "SKILL_HIT");
        BattleUnit boss = wildUnit("E_1", 100, 20);
        boss.setCurrentHp(1);
        BattleContext ctx = ctx(1L, List.of(active(player)), List.of(active(boss)), "CAPTURE");
        ctx.setBattleType("BOSS");
        ctx.setUncapturable(true);
        ctx.getAvailableCaptureBalls().put("ITEM_BALL_NORMAL", 5);

        BattleAction action = decide(ctx);

        assertEquals("SKILL", action.getType());
    }

    // ==================== 自动换宠 ====================

    @Test
    void switch_disabled_neverSwitches() {
        BattleUnit player = unit("P_1", 100, 20, "SKILL_TINY");
        player.setCurrentHp(5);
        BattleUnit bench = bench("P_2", 100, 20, "SKILL_HIT");
        BattleUnit enemy = unit("E_1", 100, 20, "SKILL_HIT");
        BattleContext ctx = ctx(1L, List.of(active(player), bench), List.of(active(enemy)), "BALANCED");
        ctx.getAutoSettings().setAutoSwitch(false);

        BattleAction action = decide(ctx);

        assertNotEquals("SWITCH", action.getType(), "autoSwitch=false 时不会自动换宠");
    }

    @Test
    void switch_lowHp_switchesToHealthyBench() {
        BattleUnit player = unit("P_1", 100, 20, "SKILL_TINY");
        player.setCurrentHp(10); // 10% < 阈值 25%
        BattleUnit bench = bench("P_2", 100, 20, "SKILL_HIT");
        BattleUnit enemy = unit("E_1", 200, 20, "SKILL_HIT");
        enemy.setDefense(500); // 高防御使攻击收益低，换宠更合理
        BattleContext ctx = ctx(1L, List.of(active(player), bench), List.of(active(enemy)), "BALANCED");

        BattleAction action = decide(ctx);

        assertEquals("SWITCH", action.getType(), "危险情况下应能合理换宠");
        assertEquals("P_2", action.getSwitchPetId());
    }

    @Test
    void switch_notPrefersLowHpBench() {
        // 候补只剩 5% HP 且攻击仍有可观收益时，不优先换上残血宠物
        BattleUnit player = unit("P_1", 100, 20, "SKILL_HIT");
        player.setCurrentHp(10);
        BattleUnit bench = bench("P_2", 100, 20, "SKILL_HIT");
        bench.setCurrentHp(5);
        BattleUnit enemy = unit("E_1", 100, 20, "SKILL_HIT");
        BattleContext ctx = ctx(1L, List.of(active(player), bench), List.of(active(enemy)), "BALANCED");

        BattleAction action = decide(ctx);

        assertEquals("SKILL", action.getType(), "不应优先换上残血宠物");
    }

    @Test
    void switch_elementAdvantageConsidered() {
        // 候补 WATER 克制敌方 FIRE：换宠评分包含属性适配
        BattleUnit player = unit("P_1", 100, 20, "SKILL_TINY");
        player.setCurrentHp(10);
        player.setElement("METAL");
        BattleUnit bench = bench("P_2", 100, 20, "SKILL_HIT");
        bench.setElement("WATER");
        BattleUnit enemy = unit("E_1", 100, 20, "SKILL_HIT");
        enemy.setElement("FIRE");
        enemy.setDefense(500);
        BattleContext ctx = ctx(1L, List.of(active(player), bench), List.of(active(enemy)), "BALANCED");

        BattleAction action = decide(ctx);

        assertEquals("SWITCH", action.getType());
        assertEquals("P_2", action.getSwitchPetId());
    }

    // ==================== 自动道具（默认关闭） ====================

    @Test
    void item_recoveryDefaultOff_neverUsed() {
        BattleUnit player = unit("P_1", 100, 20, "SKILL_TINY");
        player.setCurrentHp(20);
        BattleUnit enemy = unit("E_1", 100, 20, "SKILL_HIT");
        BattleContext ctx = ctx(1L, List.of(active(player)), List.of(active(enemy)), "BALANCED");
        ctx.getAvailableRecoveryItems().put("ITEM_POTION_SMALL", 3);
        assertFalse(ctx.getAutoSettings().isAutoUseRecoveryItem(), "默认应关闭自动恢复道具");

        BattleAction action = decide(ctx);

        assertNotEquals("ITEM", action.getType(), "autoUseRecoveryItem=false 时绝不使用道具");
    }

    @Test
    void item_recoveryEnabled_thresholdTriggers() {
        BattleUnit player = unit("P_1", 100, 20, "SKILL_TINY");
        player.setCurrentHp(20); // 20% < 阈值 35%
        BattleUnit enemy = unit("E_1", 200, 20, "SKILL_HIT");
        enemy.setDefense(500);
        BattleContext ctx = ctx(1L, List.of(active(player)), List.of(active(enemy)), "BALANCED");
        ctx.getAutoSettings().setAutoUseRecoveryItem(true);
        ctx.getAvailableRecoveryItems().put("ITEM_POTION_SMALL", 3);

        BattleAction action = decide(ctx);

        assertEquals("ITEM", action.getType(), "开启后达到阈值应使用恢复道具");
        assertEquals("ITEM_POTION_SMALL", action.getItemId());
    }

    @Test
    void item_recoveryChoosesMinimalEffective() {
        // 缺 50 HP：小药（60）与大药（500）都可用，应选最小有效方案
        BattleUnit player = unit("P_1", 100, 20, "SKILL_TINY");
        player.setCurrentHp(50);
        player.setCurrentHp(50);
        BattleUnit enemy = unit("E_1", 200, 20, "SKILL_HIT");
        enemy.setDefense(500);
        BattleContext ctx = ctx(1L, List.of(active(player)), List.of(active(enemy)), "BALANCED");
        ctx.getAutoSettings().setAutoUseRecoveryItem(true);
        ctx.getAutoSettings().setAutoRecoveryHpThreshold(0.60);
        ctx.getAvailableRecoveryItems().put("ITEM_POTION_SMALL", 3);
        ctx.getAvailableRecoveryItems().put("ITEM_POTION_BIG", 1);

        BattleAction action = decide(ctx);

        assertEquals("ITEM", action.getType());
        assertEquals("ITEM_POTION_SMALL", action.getItemId(), "应优先选择不过度浪费的恢复道具");
    }

    @Test
    void item_reviveDefaultOff_neverUsed() {
        BattleUnit player = unit("P_1", 100, 20, "SKILL_HIT");
        BattleUnit dead = unit("P_2", 100, 20, "SKILL_HIT");
        dead.setAlive(false);
        dead.setActive(false);
        BattleUnit enemy = unit("E_1", 100, 20, "SKILL_HIT");
        BattleContext ctx = ctx(1L, List.of(active(player), dead), List.of(active(enemy)), "BALANCED");
        ctx.getAvailableRecoveryItems().put("ITEM_REVIVE", 2);
        assertFalse(ctx.getAutoSettings().isAutoRevive(), "默认应关闭自动复苏");

        BattleAction action = decide(ctx);

        assertNotEquals("ITEM", action.getType(), "autoRevive=false 时不会自动复苏");
    }

    @Test
    void item_reviveEnabled_dangerousSituationUsed() {
        // 仅剩 1 只存活上场 + 敌方满状态：局势危险，开启复苏后合理复苏
        BattleUnit player = unit("P_1", 100, 20, "SKILL_TINY");
        BattleUnit dead = unit("P_2", 100, 20, "SKILL_HIT");
        dead.setAlive(false);
        dead.setActive(false);
        BattleUnit e1 = unit("E_1", 100, 20, "SKILL_HIT");
        BattleUnit e2 = unit("E_2", 100, 20, "SKILL_HIT");
        e1.setDefense(500);
        e2.setDefense(500);
        BattleContext ctx = ctx(1L, List.of(active(player), dead), List.of(active(e1), active(e2)), "BALANCED");
        ctx.getAutoSettings().setAutoRevive(true);
        ctx.getAvailableRecoveryItems().put("ITEM_REVIVE", 2);

        BattleAction action = decide(ctx);

        assertEquals("ITEM", action.getType(), "开启后危险局势应合理复苏");
        assertEquals("ITEM_REVIVE", action.getItemId());
        assertEquals("P_2", action.getTargetId());
    }

    @Test
    void item_reviveSkipped_enemyNearlyDead() {
        // 敌人仅剩 5% HP：直接结束战斗更合理，不复苏
        BattleUnit player = unit("P_1", 100, 20, "SKILL_HIT");
        BattleUnit dead = unit("P_2", 100, 20, "SKILL_HIT");
        dead.setAlive(false);
        dead.setActive(false);
        BattleUnit enemy = unit("E_1", 100, 20, "SKILL_HIT");
        enemy.setCurrentHp(5);
        BattleContext ctx = ctx(1L, List.of(active(player), dead), List.of(active(enemy)), "BALANCED");
        ctx.getAutoSettings().setAutoRevive(true);
        ctx.getAvailableRecoveryItems().put("ITEM_REVIVE", 2);

        BattleAction action = decide(ctx);

        assertNotEquals("ITEM", action.getType(), "敌人即将被斩杀时不应浪费复苏");
    }

    // ==================== 四套策略差异 ====================

    @Test
    void strategy_aggressive_prefersAttackOverModerateHeal() {
        // 自身 65% HP：均衡策略会治疗，进攻策略选择继续攻击
        BattleUnit balancedUnit = unit("P_1", 100, 20, "SKILL_HEAL_SELF", "SKILL_PLAIN30");
        balancedUnit.setCurrentHp(65);
        BattleUnit enemy1 = unit("E_1", 100, 20, "SKILL_HIT");
        BattleContext balancedCtx = ctx(1L, List.of(active(balancedUnit)), List.of(active(enemy1)), "BALANCED");
        assertEquals("SKILL_HEAL_SELF", decide(balancedCtx).getSkillId(), "均衡策略 65% HP 应治疗");

        BattleUnit aggressiveUnit = unit("P_1", 100, 20, "SKILL_HEAL_SELF", "SKILL_PLAIN30");
        aggressiveUnit.setCurrentHp(65);
        BattleUnit enemy2 = unit("E_1", 100, 20, "SKILL_HIT");
        BattleContext aggressiveCtx = ctx(2L, List.of(active(aggressiveUnit)), List.of(active(enemy2)), "AGGRESSIVE");
        assertEquals("SKILL_PLAIN30", decide(aggressiveCtx).getSkillId(), "进攻策略应倾向继续攻击");
    }

    @Test
    void strategy_aggressive_survivalFloorStillWorks() {
        // 进攻策略 HP 极低（< 15%）时生存修正回升，仍会治疗
        BattleUnit player = unit("P_1", 100, 20, "SKILL_HEAL_SELF", "SKILL_TINY");
        player.setCurrentHp(10);
        BattleUnit enemy = unit("E_1", 100, 20, "SKILL_HIT");
        BattleContext ctx = ctx(1L, List.of(active(player)), List.of(active(enemy)), "AGGRESSIVE");

        BattleAction action = decide(ctx);

        assertEquals("SKILL_HEAL_SELF", action.getSkillId(), "进攻策略不能完全无视死亡风险");
    }

    @Test
    void strategy_defensive_earlyHeal() {
        // 稳健策略 45% HP（< 50% 提前恢复阈值）即优先治疗
        BattleUnit player = unit("P_1", 100, 20, "SKILL_HEAL_SELF", "SKILL_PLAIN30");
        player.setCurrentHp(45);
        BattleUnit enemy = unit("E_1", 100, 20, "SKILL_HIT");
        BattleContext ctx = ctx(1L, List.of(active(player)), List.of(active(enemy)), "DEFENSIVE");

        BattleAction action = decide(ctx);

        assertEquals("SKILL_HEAL_SELF", action.getSkillId(), "稳健策略低 HP 应优先恢复");
    }

    @Test
    void strategy_defensive_stillFinishesKill() {
        // 稳健策略敌方残血时仍完成击杀（不无限防御）
        BattleUnit player = unit("P_1", 100, 20, "SKILL_HIT");
        BattleUnit enemy = unit("E_1", 100, 20, "SKILL_HIT");
        enemy.setCurrentHp(5);
        BattleContext ctx = ctx(1L, List.of(active(player)), List.of(active(enemy)), "DEFENSIVE");

        BattleAction action = decide(ctx);

        assertEquals("SKILL", action.getType(), "敌人残血时稳健策略仍应完成击杀");
        assertEquals("E_1", action.getTargetId());
    }

    @Test
    void strategy_capture_assistSkillsBoosted() {
        // CAPTURE 策略下 CAPTURE_ASSIST（留生一击）权重大幅提高
        BattleUnit player = unit("P_1", 100, 20, "SKILL_LEAVE_ALIVE", "SKILL_PLAIN30");
        BattleUnit wild = wildUnit("E_1", 100, 20);
        wild.setCurrentHp(35); // 危险区
        BattleContext ctx = ctx(1L, List.of(active(player)), List.of(active(wild)), "CAPTURE");
        ctx.getAvailableCaptureBalls().put("ITEM_BALL_NORMAL", 5);

        BattleAction action = decide(ctx);

        assertEquals("SKILL_LEAVE_ALIVE", action.getSkillId());
    }

    // ==================== 定位 ====================

    @Test
    void role_speciesRoleConfigAffectsDecision() {
        // 种族配置 role=DAMAGE：攻击权重提高、治疗权重下降 → 65% HP 仍选择攻击
        BattleUnit player = unit("P_1", 100, 20, "SKILL_HEAL_SELF", "SKILL_PLAIN30");
        player.setCurrentHp(65);
        player.setSpeciesId("PET_DAMAGE_ROLE");
        BattleUnit enemy = unit("E_1", 100, 20, "SKILL_HIT");
        BattleContext ctx = ctx(1L, List.of(active(player)), List.of(active(enemy)), "BALANCED");

        BattleAction action = decide(ctx);

        assertEquals("SKILL_PLAIN30", action.getSkillId(), "输出定位应更倾向攻击（定位非硬性限制）");
    }

    @Test
    void role_notHardRestriction_supportCanKill() {
        // 辅助定位（有治疗技能推断 SUPPORT）面对 1HP 敌人仍完成击杀
        BattleUnit player = unit("P_1", 100, 20, "SKILL_HEAL_SELF", "SKILL_HIT");
        BattleUnit enemy = unit("E_1", 100, 20, "SKILL_HIT");
        enemy.setCurrentHp(1);
        BattleContext ctx = ctx(1L, List.of(active(player)), List.of(active(enemy)), "BALANCED");

        BattleAction action = decide(ctx);

        assertEquals("SKILL_HIT", action.getSkillId(), "定位不是硬性行为限制，可斩杀时应击杀");
    }

    // ==================== 测试辅助 ====================

    /** 决策单个单位（取第一个玩家单位的行动）。 */
    private BattleAction decide(BattleContext ctx) {
        List<BattleAction> actions = provider.decide(ctx, ctx.getPlayerSide());
        assertFalse(actions.isEmpty(), "决策不应为空");
        return actions.get(0);
    }

    /** 构建战斗上下文并设置自动战斗策略。 */
    private BattleContext ctx(long seed, List<BattleUnit> playerUnits, List<BattleUnit> enemyUnits,
                              String strategy) {
        BattleContext ctx = new BattleContext("BATTLE_AUTO_TEST", seed);
        ctx.setBattleType("TEST");
        BattleSide player = new BattleSide("PLAYER");
        player.getUnits().addAll(playerUnits);
        BattleSide enemy = new BattleSide("ENEMY");
        enemy.getUnits().addAll(enemyUnits);
        ctx.setPlayerSide(player);
        ctx.setEnemySide(enemy);
        AutoBattleSettings settings = new AutoBattleSettings();
        settings.setEnabled(true);
        settings.setStrategy(strategy);
        ctx.setAutoSettings(settings);
        return ctx;
    }

    /** 玩家单位（存活上场）。 */
    private BattleUnit unit(String id, int maxHp, int strength, String... skills) {
        BattleUnit unit = new BattleUnit();
        unit.setUnitId(id);
        unit.setName(id);
        unit.setElement("NONE");
        unit.setLevel(10);
        unit.setMaxHp(maxHp);
        unit.setCurrentHp(maxHp);
        unit.setStrength(strength);
        unit.setSpirit(strength);
        unit.setDefense(0);
        unit.setResistance(0);
        unit.setSpeed(10);
        unit.setControlResistance(1.0);
        unit.setActive(true);
        unit.setPosition(0);
        unit.getSkillIds().addAll(List.of(skills));
        return unit;
    }

    /** 候补单位（存活但未上场）。 */
    private BattleUnit bench(String id, int maxHp, int strength, String... skills) {
        BattleUnit unit = unit(id, maxHp, strength, skills);
        unit.setActive(false);
        unit.setPosition(-1);
        return unit;
    }

    /** 野生单位（携带 wildData，可捕捉）。 */
    private BattleUnit wildUnit(String id, int maxHp, int strength) {
        BattleUnit unit = unit(id, maxHp, strength, "SKILL_HIT");
        unit.setSpeciesId("PET_WILD");
        BattleUnit.WildUnitData wildData = new BattleUnit.WildUnitData();
        unit.setWildData(wildData);
        return unit;
    }

    private BattleUnit active(BattleUnit unit) {
        return unit; // unit() 已设置 active
    }

    /** 构建测试配置注册中心（程序化，不依赖 YAML）。 */
    private GameConfigRegistry buildRegistry() {
        try {
            SystemRuleConfig system = new SystemRuleConfig(); // AutoBattleConfig 默认值
            GameConfigRegistry registry = new GameConfigRegistry(null, null);
            setField(registry, "systemRules", system);

            // 属性与克制（WATER > FIRE > METAL）
            GameElementsConfig elements = new GameElementsConfig();
            List<GameElementConfig> elementList = new ArrayList<>();
            for (String id : List.of("WATER", "FIRE", "METAL", "NONE")) {
                GameElementConfig elem = new GameElementConfig();
                elem.setId(id);
                elem.setName(id);
                elementList.add(elem);
            }
            elements.setElements(elementList);
            setField(registry, "elementsConfig", elements);
            setField(registry, "advantageIndex", new java.util.HashSet<>(
                    List.of("WATER|FIRE", "FIRE|METAL")));

            // 技能
            Map<String, SkillConfig> skillIndex = new LinkedHashMap<>();
            addSkill(skillIndex, skill("SKILL_HIT", "NONE", "DAMAGE", "ENEMY_SINGLE", 30, 0));
            addSkill(skillIndex, skill("SKILL_WATER_HIT", "WATER", "DAMAGE", "ENEMY_SINGLE", 30, 0));
            addSkill(skillIndex, skill("SKILL_NEUTRAL", "NONE", "DAMAGE", "ENEMY_SINGLE", 30, 0));
            addSkill(skillIndex, skill("SKILL_TINY", "NONE", "DAMAGE", "ENEMY_SINGLE", 5, 0));
            addSkill(skillIndex, skill("SKILL_PLAIN30", "NONE", "DAMAGE", "ENEMY_SINGLE", 40, 0));
            addSkill(skillIndex, skill("SKILL_BIG40", "NONE", "DAMAGE", "ENEMY_SINGLE", 40, 0));
            addSkill(skillIndex, skill("SKILL_BIG1000", "NONE", "DAMAGE", "ENEMY_SINGLE", 1000, 4));
            addSkill(skillIndex, skill("SKILL_SMALL", "NONE", "DAMAGE", "ENEMY_SINGLE", 10, 0));
            SkillConfig healSelf = skill("SKILL_HEAL_SELF", "NONE", "HEAL", "SELF", 50, 0);
            addSkill(skillIndex, healSelf);
            SkillConfig healAlly = skill("SKILL_HEAL_ALLY", "NONE", "HEAL", "ALLY_SINGLE", 50, 0);
            addSkill(skillIndex, healAlly);
            // 控制技能（附加 SPECIAL_CONTROL 状态）
            SkillConfig control = skill("SKILL_CONTROL", "NONE", "NONE", "ENEMY_SINGLE", 0, 0);
            control.getEffects().add(effect("APPLY_STATUS", "CONFUSE", 1.0));
            addSkill(skillIndex, control);
            // 驱散技能
            SkillConfig dispel = skill("SKILL_DISPEL", "NONE", "NONE", "ENEMY_SINGLE", 0, 0);
            dispel.getEffects().add(effect("DISPEL", null, 1.0));
            addSkill(skillIndex, dispel);
            // 破盾技能（同基础值 30 + SHIELD_BREAK 标签）
            SkillConfig breaker = skill("SKILL_BREAKER", "NONE", "DAMAGE", "ENEMY_SINGLE", 30, 0);
            breaker.setTags(List.of("SHIELD_BREAK"));
            addSkill(skillIndex, breaker);
            // 吸血技能（基础 28 + 30% 吸血，低于普通 30）
            SkillConfig drain = skill("SKILL_DRAIN", "NONE", "DAMAGE", "ENEMY_SINGLE", 28, 0);
            SkillConfig.SkillEffectConfig drainEffect = effect("LIFE_STEAL", null, 1.0);
            drainEffect.setPercent(0.3);
            drain.getEffects().add(drainEffect);
            addSkill(skillIndex, drain);
            // 终结技能（基础 30 + FINISHER 标签）
            SkillConfig finish = skill("SKILL_FINISH", "NONE", "DAMAGE", "ENEMY_SINGLE", 30, 0);
            finish.setTags(List.of("FINISHER"));
            addSkill(skillIndex, finish);
            // 留生一击（基础 30 + LEAVE_AT_ONE_HP + CAPTURE_ASSIST）
            SkillConfig leaveAlive = skill("SKILL_LEAVE_ALIVE", "NONE", "DAMAGE", "ENEMY_SINGLE", 30, 0);
            leaveAlive.getEffects().add(effect("LEAVE_AT_ONE_HP", null, 1.0));
            leaveAlive.setTags(List.of("CAPTURE_ASSIST"));
            addSkill(skillIndex, leaveAlive);
            // 命运天平（HP_PERCENT_EXCHANGE）
            SkillConfig balance = skill("SKILL_BALANCE", "NONE", "NONE", "ENEMY_SINGLE", 0, 0);
            balance.getEffects().add(effect("HP_PERCENT_EXCHANGE", null, 1.0));
            addSkill(skillIndex, balance);
            setField(registry, "skillIndex", skillIndex);
            SkillsConfig skillsConfig = new SkillsConfig();
            skillsConfig.setSkills(new ArrayList<>(skillIndex.values()));
            setField(registry, "skillsConfig", skillsConfig);

            // 状态
            Map<String, StatusEffectConfig> statusIndex = new LinkedHashMap<>();
            statusIndex.put("CONFUSE", status("CONFUSE", "SPECIAL_CONTROL", false));
            statusIndex.put("SILENCE", status("SILENCE", "SPECIAL_CONTROL", false));
            statusIndex.put("CAPTURE_STUN", status("CAPTURE_STUN", "SPECIAL_CONTROL", true));
            statusIndex.put("ATK_UP", status("ATK_UP", "BUFF", false));
            statusIndex.put("DEF_UP", status("DEF_UP", "BUFF", false));
            statusIndex.put("BURN", status("BURN", "CONTINUOUS", false));
            setField(registry, "statusIndex", statusIndex);
            StatusesConfig statusesConfig = new StatusesConfig();
            statusesConfig.setStatuses(new ArrayList<>(statusIndex.values()));
            setField(registry, "statusesConfig", statusesConfig);

            // 道具
            Map<String, ItemConfig> itemIndex = new LinkedHashMap<>();
            itemIndex.put("ITEM_BALL_NORMAL", item("ITEM_BALL_NORMAL", "CAPTURE_BALL", 1.0));
            itemIndex.put("ITEM_POTION_SMALL", item("ITEM_POTION_SMALL", "HEAL_HP", 60));
            itemIndex.put("ITEM_POTION_BIG", item("ITEM_POTION_BIG", "HEAL_HP", 500));
            itemIndex.put("ITEM_REVIVE", item("ITEM_REVIVE", "REVIVE", 0.5));
            setField(registry, "itemIndex", itemIndex);
            ItemsConfig itemsConfig = new ItemsConfig();
            itemsConfig.setItems(new ArrayList<>(itemIndex.values()));
            setField(registry, "itemsConfig", itemsConfig);

            // 种族（野生可捕捉 + 输出定位测试种族）
            Map<String, PetSpeciesConfig> speciesIndex = new LinkedHashMap<>();
            PetSpeciesConfig wild = new PetSpeciesConfig();
            wild.setId("PET_WILD");
            wild.setName("野生测试");
            wild.setCaptureRate(0.5);
            speciesIndex.put("PET_WILD", wild);
            PetSpeciesConfig damageRole = new PetSpeciesConfig();
            damageRole.setId("PET_DAMAGE_ROLE");
            damageRole.setName("输出定位");
            damageRole.setRole("DAMAGE");
            damageRole.setCaptureRate(0.5);
            speciesIndex.put("PET_DAMAGE_ROLE", damageRole);
            setField(registry, "speciesIndex", speciesIndex);
            PetsConfig petsConfig = new PetsConfig();
            petsConfig.setSpecies(new ArrayList<>(speciesIndex.values()));
            setField(registry, "petsConfig", petsConfig);

            return registry;
        } catch (Exception e) {
            throw new IllegalStateException("测试配置构建失败", e);
        }
    }

    private static void addSkill(Map<String, SkillConfig> index, SkillConfig skill) {
        index.put(skill.getId(), skill);
    }

    private static SkillConfig skill(String id, String element, String effectType, String target,
                                     double baseValue, int cooldown) {
        SkillConfig skill = new SkillConfig();
        skill.setId(id);
        skill.setName(id);
        skill.setElement(element);
        skill.setDamageType("NONE");
        skill.setEffectType(effectType);
        skill.setTarget(target);
        skill.setBaseValue(baseValue);
        skill.setCooldown(cooldown);
        skill.setAccuracy(1.0);
        return skill;
    }

    private static SkillConfig.SkillEffectConfig effect(String type, String statusId, double chance) {
        SkillConfig.SkillEffectConfig effect = new SkillConfig.SkillEffectConfig();
        effect.setType(type);
        effect.setStatusId(statusId);
        effect.setChance(chance);
        return effect;
    }

    private static StatusEffectConfig status(String id, String category, boolean captureStun) {
        StatusEffectConfig config = new StatusEffectConfig();
        config.setId(id);
        config.setName(id);
        config.setCategory(category);
        config.setDefaultDuration(2);
        config.setCaptureStun(captureStun);
        if ("SILENCE".equals(id)) {
            config.setSilence(true);
        }
        if (captureStun) {
            config.setCaptureBonus(false);
        }
        return config;
    }

    private static ItemConfig item(String id, String itemType, double value) {
        ItemConfig config = new ItemConfig();
        config.setId(id);
        config.setName(id);
        config.setCategory("RECOVERY");
        config.setItemType(itemType);
        config.setValue(value);
        return config;
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = GameConfigRegistry.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
