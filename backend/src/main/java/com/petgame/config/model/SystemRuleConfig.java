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

    // ---- Boss ----

    /** Boss 幸运值兑换消耗（默认 100）。 */
    private int luckyExchangeCost = 100;
}
