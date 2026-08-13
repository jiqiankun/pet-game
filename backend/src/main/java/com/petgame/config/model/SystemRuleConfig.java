package com.petgame.config.model;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 系统规则配置。
 * <p>
 * 对应 system.yml，定义游戏核心数值规则。
 * 所有倍率、上限、数量等均可通过外部配置覆盖。
 */
@Data
@NoArgsConstructor
public class SystemRuleConfig {

    /** 配置结构版本。 */
    private int configVersion = 1;

    // ---- 属性克制 ----

    /** 克制倍率（默认 ×1.50）。 */
    private double advantageMultiplier = 1.50;

    /** 普通倍率（默认 ×1.00）。 */
    private double neutralMultiplier = 1.00;

    /** 被克制倍率（默认 ×0.75）。 */
    private double disadvantageMultiplier = 0.75;

    /** 本属性技能加成倍率（默认 ×1.20）。 */
    private double sameElementBonus = 1.20;

    // ---- 暴击 ----

    /** 基础暴击率（默认 0.05 = 5%）。 */
    private double critRate = 0.05;

    /** 暴击倍率下限（默认 1.4）。 */
    private double critMultiplierMin = 1.4;

    /** 暴击倍率上限（默认 2.0）。 */
    private double critMultiplierMax = 2.0;

    // ---- 等级与属性点 ----

    /** 等级上限（默认 50）。 */
    private int levelCap = 50;

    /** 每级自由属性点数（默认 3）。 */
    private int freePointsPerLevel = 3;

    /** 稀有度每 10 级额外自由点数：key=稀有度 ID（COMMON/RARE/EPIC/LEGENDARY），value=每 10 级额外点数（需求 §19：0/2/4/6）。 */
    private java.util.Map<String, Integer> rarityExtraPointsPer10Levels = new java.util.HashMap<>();

    /** 升级经验公式基数：升到下一级所需经验 = expBase * expGrowthFactor^(当前等级-1)（需求 §17，数值设计缺失→配置化）。 */
    private int expBase = 100;

    /** 升级经验公式增长系数（默认 1.15）。 */
    private double expGrowthFactor = 1.15;

    /** 加 1 点自由属性到生命/力量/灵力/防御/抗性 消耗的自由点数（需求 §20，默认 1）。 */
    private int statPointCost = 1;

    /** 加 1 点自由属性到生命（+5HP）消耗的自由点数（默认 1）。 */
    private int hpPointCost = 1;

    /** 加 1 点速度消耗的自由点数（需求 §20，速度成本较高，默认 2）。 */
    private int speedPointCost = 2;

    // ---- 队伍 ----

    /** 最大携带宠物数（默认 6）。 */
    private int maxCarryPets = 6;

    /** 标准上场宠物数（默认 3）。 */
    private int standardBattleSlots = 3;

    /** 队伍预设套数（默认 5）。 */
    private int maxTeamPresets = 5;

    // ---- 资质 ----

    /** 资质最小值。 */
    private int aptitudeMin = 0;

    /** 资质最大值。 */
    private int aptitudeMax = 100;

    // ---- 个体基础浮动 ----

    /** 个体六维基础浮动比例（默认 ±5% = 0.05）。 */
    private double baseStatVariance = 0.05;

    // ---- 战斗结算（阶段 3）----

    /** 防御/抗性减伤公式常数：raw × K / (K + def)，K 越大减伤越平缓（默认 200）。 */
    private double defenseMitigationConstant = 200.0;

    /** 防御行动减伤比例（默认 0.50 = 受到伤害减半）。 */
    private double defendDamageReduction = 0.50;

    /** 正常命中最低伤害（默认 1）。 */
    private int minDamage = 1;

    /** 自由属性点单点属性加成（默认 1.0）。 */
    private double freePointStatValue = 1.0;

    /** 自由属性点单点 HP 加成（默认 5.0）。 */
    private double freePointHpValue = 5.0;

    /** 每级六维固定成长值（阶段 3 临时公式，阶段 4 养成体系正式化，默认 2.0）。 */
    private double levelStatGrowth = 2.0;

    /** 每级 HP 固定成长值（默认 8.0）。 */
    private double levelHpGrowth = 8.0;

    // ---- 放生 ----

    /** 放生培养加成上限（默认 50% = 0.50）。 */
    private double releaseBonusMax = 0.50;

    // ---- 捕捉（阶段 5）----

    /** HP 系数参数：捕获率 = 基础捕获率 × (1 - captureHpFactor × 当前HP比例)，满血惩罚、空血 1.0（默认 0.5）。 */
    private double captureHpFactor = 0.5;

    /** 每个异常状态（DEBUFF/CONTROL）的捕获率加成（默认 +0.15）。 */
    private double statusCaptureBonus = 0.15;

    /** 异常状态捕获加成最多计数的状态个数（默认 2）。 */
    private int captureStatusMaxCount = 2;

    /** 精英个体捕捉倍率（决策一，默认 ×0.6；本阶段无精英个体固定 1.0）。 */
    private double eliteCaptureMultiplier = 0.6;

    /** 逃跑成功率（用户裁决：必定成功，默认 1.0，配置化可调）。 */
    private double fleeSuccessRate = 1.0;

    /** 野生宠物低概率携带稀有技能的概率（默认 0.05）。 */
    private double rareSkillChance = 0.05;

    /** 野生宠物极低概率特殊外观的概率（默认 0.01）。 */
    private double specialAppearanceChance = 0.01;

    /** 放生礼物价值点数基础值：key=稀有度（COMMON/RARE/EPIC/LEGENDARY），value=点数（决策七：20/60/150/400）。 */
    private java.util.Map<String, Integer> releaseGiftBaseValue = new java.util.HashMap<>();

    /** 捕获等级系数：每级加成比例（决策七，默认 0.01 = 1%）。 */
    private double releaseLevelFactorPerLevel = 0.01;

    /** 捕获等级系数上限（决策七，默认 ×1.5）。 */
    private double releaseLevelFactorCap = 1.5;

    /** 培养系数上限（决策七，对应培养加成 ≤50%，默认 1.5）。 */
    private double releaseCultivationFactorMax = 1.5;

    /** 培养系数满值所需已分配自由点数（培养系数 = 1 + min(已分配/该值, 1) × (上限-1)，默认 100）。 */
    private int releaseCultivationPointsCap = 100;

    /** 放生高资质额外警告阈值（平均资质 ≥ 该值时前端二次确认，默认 90，配置化）。 */
    private int releaseWarningAptitudeThreshold = 90;

    /** 野生战斗奖励稀有度系数：key=稀有度，value=系数（用户裁决：普通 1.0/稀有 1.2/珍稀 1.5/传说 2.0）。 */
    private java.util.Map<String, Double> wildRewardRarityMultiplier = new java.util.HashMap<>();

    // ---- Boss ----

    /** Boss 幸运值兑换消耗（默认 100）。 */
    private int luckyExchangeCost = 100;

    /** Boss AI 决策参数（BossDecisionProvider 评分权重，均不影响实际战斗结算）。 */
    private BossAiConfig bossAi = new BossAiConfig();

    // ---- Boss 控制抗性（阶段 7，需求 §43 + 决策六）----

    /** 控制抗性：key = NORMAL/ELITE/BOSS，value = 异常成功率修正系数。 */
    private java.util.Map<String, Double> controlResistance = new java.util.HashMap<>();

    /** 连续控制衰减系数：第 1/2/3 次施加控制的成功率修正。 */
    private java.util.List<Double> consecutiveControlDecay = new java.util.ArrayList<>();

    /** 第 4 次及以后保持的下限。 */
    private double consecutiveControlMin = 0.4;

    /** 连续 N 回合未受控归零控制计数。 */
    private int controlDecayResetRounds = 2;

    // ---- HP 百分比交换（REV-005，需求 §147 命运天平）----

    /** Boss 受 HP 百分比交换的幅度上限（单次最多改变的百分点，默认 0.20 = 20 个百分点）。 */
    private double bossHpExchangeLimit = 0.20;

    // ---- 图鉴（阶段 8，决策五）----

    /** 图鉴研究值配置（阶段 8）。 */
    private PokedexRuleConfig pokedex = new PokedexRuleConfig();

    /**
     * Boss AI 决策参数（阶段 7 Boss AI 改造）。
     * <p>
     * 仅供 BossDecisionProvider 评分使用，不参与实际战斗结算；
     * 不读取玩家等级/战力，难度仅来源于 Boss 配置与战场状态（需求 §80）。
     */
    @lombok.Data
    @lombok.NoArgsConstructor
    public static class BossAiConfig {

        /** 治疗触发阈值：友方 HP% 低于该值时治疗优先级明显提高（默认 0.40）。 */
        private double healTriggerHpPercent = 0.40;

        /** 治疗免费阈值：友方 HP% 高于该值时治疗候选大幅降权（默认 0.90）。 */
        private double healNoNeedHpPercent = 0.90;

        /** 低血治疗紧迫度倍率（HP% < healTriggerHpPercent 时治疗分 ×该值，默认 1.8）。 */
        private double healUrgencyMultiplier = 1.8;

        /** 斩杀奖励比例：预计伤害可击杀目标时，额外加 估算伤害 × 该值（默认 0.6）。 */
        private double killBonusPercent = 0.6;

        /** 低血目标加权：攻击分 × (1 + 该值 × (1 - 目标HP%))（默认 0.5）。 */
        private double lowHpTargetWeight = 0.5;

        /** 控制技能基础分（默认 60）。 */
        private double controlBaseScore = 60.0;

        /** 减益/增益技能基础分（默认 40）。 */
        private double utilityBaseScore = 40.0;

        /** 目标已受控/已携带同名状态时的评分惩罚倍率（默认 0.10，避免机械重复控制）。 */
        private double existingControlPenalty = 0.10;

        /** 攻击技能附加状态效果的估值比例（chance × 该值 × 估算伤害，默认 0.10）。 */
        private double statusEffectBonus = 0.10;

        /** 接近分容差：评分 ≥ 最高分 × (1 - 该值) 的候选间随机选择（默认 0.05）。 */
        private double tieTolerance = 0.05;

        /** 阶段攻击倍率（索引 = 已激活阶段触发器数量，越界取末项）。 */
        private java.util.List<Double> phaseAttackMultipliers = java.util.List.of(1.0, 1.3, 1.6);

        /** 阶段控制/辅助倍率（三阶段关键控制回升）。 */
        private java.util.List<Double> phaseControlMultipliers = java.util.List.of(1.0, 0.8, 1.2);

        /** 阶段治疗倍率（后期降权，允许爆发优先于小额治疗）。 */
        private java.util.List<Double> phaseHealMultipliers = java.util.List.of(1.0, 0.8, 0.6);
    }

    /**
     * 图鉴研究值配置（阶段 8，决策五）。
     * <p>
     * 研究等级门槛、研究值来源分值、资质预估等级标签全部配置化。
     */
    @lombok.Data
    @lombok.NoArgsConstructor
    public static class PokedexRuleConfig {

        /** 研究等级门槛：key=等级(1~5)，value=累计研究值。默认 Lv.1=10/Lv.2=30/Lv.3=60/Lv.4=100/Lv.5=150。 */
        private java.util.Map<String, Integer> levelThresholds = new java.util.LinkedHashMap<>();

        /** 首次发现研究值（默认 5）。 */
        private int firstDiscoveryPoints = 5;
        /** 首次捕获研究值（默认 10）。 */
        private int firstCapturePoints = 10;
        /** 后续每次捕获研究值（默认 2）。 */
        private int subsequentCapturePoints = 2;
        /** 使用该宠物战斗研究值（默认 1）。 */
        private int battleParticipationPoints = 1;
        /** 使用该宠物获胜研究值（默认 1）。 */
        private int battleWinPoints = 1;
        /** 等级解锁新技能研究值（默认 2）。 */
        private int skillUnlockPoints = 2;
        /** 捕获综合资质 ≥80 个体研究值（默认 5）。 */
        private int highAptitude80Points = 5;
        /** 捕获综合资质 ≥90 个体研究值（默认 8）。 */
        private int highAptitude90Points = 8;
        /** 发现稀有技能研究值（默认 5）。 */
        private int rareSkillDiscoveryPoints = 5;
        /** 发现特殊外观研究值（默认 10）。 */
        private int specialAppearancePoints = 10;
        /** 捕获精英个体研究值（默认 8）。 */
        private int eliteCapturePoints = 8;

        /** 资质预估等级标签：key=等级标签(S/A/B/C/D)，value=最低综合资质。默认 S=90/A=80/B=65/C=50。 */
        private java.util.Map<String, Integer> aptitudeGrades = new java.util.LinkedHashMap<>();
    }
}
