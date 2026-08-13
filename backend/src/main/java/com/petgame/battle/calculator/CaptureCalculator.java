package com.petgame.battle.calculator;

import com.petgame.battle.model.BattleUnit;
import com.petgame.battle.model.StatusInstance;
import com.petgame.config.model.StatusEffectConfig;
import com.petgame.config.model.SystemRuleConfig;

import java.util.Map;

/**
 * 捕捉率计算器（阶段 5，需求 §46）。
 * <p>
 * 捕捉率 = 基础捕获率（物种配置）
 * × (1 − captureHpFactor × 当前HP比例)   —— HP 越低越容易捕捉，满血惩罚、空血 1.0
 * × (1 + statusCaptureBonus × min(异常数, captureStatusMaxCount))  —— 异常状态加成，计数封顶
 * × 捕捉球倍率（道具配置）
 * × 精英系数（本阶段无精英个体，固定 1.0）
 * <p>
 * 结果收敛到 [0, 1]。纯函数实现，不依赖随机源，便于单元测试。
 */
public final class CaptureCalculator {

    private CaptureCalculator() {
    }

    /**
     * 计算捕捉率。
     *
     * @param baseCaptureRate 物种基础捕获率（0~1）
     * @param hpRatio         目标当前 HP 比例（0~1，满血 = 1）
     * @param statusCount     目标携带的异常状态（DEBUFF/CONTROL）数量
     * @param ballMultiplier  捕捉球倍率
     * @param eliteMultiplier 精英个体捕捉倍率
     * @param rules           系统规则配置（捕捉参数来源）
     * @return 捕捉率，收敛到 [0, 1]
     */
    public static double computeCaptureRate(double baseCaptureRate, double hpRatio,
                                            int statusCount, double ballMultiplier,
                                            double eliteMultiplier, SystemRuleConfig rules) {
        double hpFactor = 1.0 - rules.getCaptureHpFactor() * hpRatio;
        int countedStatus = Math.min(Math.max(0, statusCount), rules.getCaptureStatusMaxCount());
        double statusFactor = 1.0 + rules.getStatusCaptureBonus() * countedStatus;
        double rate = baseCaptureRate * hpFactor * statusFactor * ballMultiplier * eliteMultiplier;
        return Math.max(0.0, Math.min(1.0, rate));
    }

    /**
     * 统计目标携带的异常状态数量（仅 DEBUFF/CONTROL 类计入捕捉加成）。
     */
    public static int countCaptureBonusStatuses(BattleUnit target,
                                                Map<String, StatusEffectConfig> statusIndex) {
        if (target == null || target.getStatuses() == null) {
            return 0;
        }
        int count = 0;
        for (StatusInstance status : target.getStatuses()) {
            StatusEffectConfig config = statusIndex.get(status.getStatusId());
            if (config != null && ("DEBUFF".equals(config.getCategory())
                    || "CONTROL".equals(config.getCategory()))) {
                count++;
            }
        }
        return count;
    }
}
