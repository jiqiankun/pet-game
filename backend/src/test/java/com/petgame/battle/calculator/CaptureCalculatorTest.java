package com.petgame.battle.calculator;

import com.petgame.battle.model.BattleUnit;
import com.petgame.battle.model.StatusInstance;
import com.petgame.config.model.StatusEffectConfig;
import com.petgame.config.model.SystemRuleConfig;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 捕捉率计算测试（阶段 5，测试规约 §2.2）。
 * <p>
 * 公式：基础捕获率 × (1 − captureHpFactor × HP比例) × (1 + statusBonus × min(异常数, 2))
 * × 球倍率 × 精英系数，clamp [0, 1]。
 */
class CaptureCalculatorTest {

    /** 默认规则：hpFactor=0.5、statusBonus=0.15、状态计数上限 2。 */
    private SystemRuleConfig defaultRules() {
        return new SystemRuleConfig();
    }

    @Test
    void lowerHp_shouldIncreaseCaptureRate() {
        SystemRuleConfig rules = defaultRules();
        double fullHp = CaptureCalculator.computeCaptureRate(0.5, 1.0, 0, 1.0, 1.0, rules);
        double halfHp = CaptureCalculator.computeCaptureRate(0.5, 0.5, 0, 1.0, 1.0, rules);
        double lowHp = CaptureCalculator.computeCaptureRate(0.5, 0.1, 0, 1.0, 1.0, rules);
        assertTrue(fullHp < halfHp && halfHp < lowHp, "HP 越低捕获率越高");
        // 空血时 HP 系数 = 1.0
        assertEquals(0.5, CaptureCalculator.computeCaptureRate(0.5, 0.0, 0, 1.0, 1.0, rules), 1e-9);
        // 满血惩罚：0.5 × (1 - 0.5×1) = 0.25
        assertEquals(0.25, fullHp, 1e-9);
    }

    @Test
    void statusEffects_shouldBoostCaptureRate_withCap() {
        SystemRuleConfig rules = defaultRules();
        double base = CaptureCalculator.computeCaptureRate(0.5, 0.0, 0, 1.0, 1.0, rules);
        double oneStatus = CaptureCalculator.computeCaptureRate(0.5, 0.0, 1, 1.0, 1.0, rules);
        double twoStatus = CaptureCalculator.computeCaptureRate(0.5, 0.0, 2, 1.0, 1.0, rules);
        double fiveStatus = CaptureCalculator.computeCaptureRate(0.5, 0.0, 5, 1.0, 1.0, rules);
        assertTrue(oneStatus > base, "异常状态提升捕获率");
        assertEquals(base * 1.15, oneStatus, 1e-9);
        assertEquals(base * 1.30, twoStatus, 1e-9);
        // 超过 2 个状态按 2 个计数
        assertEquals(twoStatus, fiveStatus, 1e-9);
    }

    @Test
    void ballMultiplier_threeTiers() {
        SystemRuleConfig rules = defaultRules();
        double normal = CaptureCalculator.computeCaptureRate(0.4, 0.0, 0, 1.0, 1.0, rules);
        double great = CaptureCalculator.computeCaptureRate(0.4, 0.0, 0, 1.5, 1.0, rules);
        double ultra = CaptureCalculator.computeCaptureRate(0.4, 0.0, 0, 2.5, 1.0, rules);
        assertEquals(0.4, normal, 1e-9);
        assertEquals(0.6, great, 1e-9);
        assertEquals(1.0, ultra, 1e-9); // clamp 上限
    }

    @Test
    void eliteMultiplier_shouldReduceRate() {
        SystemRuleConfig rules = defaultRules();
        double normal = CaptureCalculator.computeCaptureRate(0.5, 0.0, 0, 1.0, 1.0, rules);
        double elite = CaptureCalculator.computeCaptureRate(0.5, 0.0, 0, 1.0, 0.6, rules);
        assertEquals(normal * 0.6, elite, 1e-9);
    }

    @Test
    void rate_shouldClampBetweenZeroAndOne() {
        SystemRuleConfig rules = defaultRules();
        // 高基础 × 高倍率 → 封顶 1.0
        assertEquals(1.0, CaptureCalculator.computeCaptureRate(1.0, 0.0, 2, 2.5, 1.0, rules), 1e-9);
        // 任何输入不低于 0
        assertTrue(CaptureCalculator.computeCaptureRate(0.0, 1.0, 0, 1.0, 1.0, rules) >= 0.0);
    }

    @Test
    void countStatusBonuses_onlyDebuffAndControl() {
        Map<String, StatusEffectConfig> statusIndex = new HashMap<>();
        statusIndex.put("BURN", status("BURN", "DOT"));
        statusIndex.put("WEAKEN", status("WEAKEN", "DEBUFF"));
        statusIndex.put("STUN", status("STUN", "CONTROL"));
        statusIndex.put("ATK_UP", status("ATK_UP", "BUFF"));

        BattleUnit unit = new BattleUnit();
        unit.getStatuses().add(new StatusInstance("BURN", 2, null));
        unit.getStatuses().add(new StatusInstance("WEAKEN", 2, null));
        unit.getStatuses().add(new StatusInstance("STUN", 1, null));
        unit.getStatuses().add(new StatusInstance("ATK_UP", 3, null));

        // 仅 DEBUFF/CONTROL 计入（BURN 是 DOT、ATK_UP 是 BUFF，不计）
        assertEquals(2, CaptureCalculator.countCaptureBonusStatuses(unit, statusIndex));
        assertEquals(0, CaptureCalculator.countCaptureBonusStatuses(null, statusIndex));
    }

    private StatusEffectConfig status(String id, String category) {
        StatusEffectConfig config = new StatusEffectConfig();
        config.setId(id);
        config.setCategory(category);
        return config;
    }
}
