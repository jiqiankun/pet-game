package com.petgame.config.model;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 状态配置模型（阶段 3）。
 * <p>
 * 对应 statuses/statuses.yml 中的单个状态条目。状态效果全部配置化，
 * 引擎按字段统一解释，不针对具体状态 ID 写分支。
 * <p>
 * 状态类别：
 * <ul>
 *   <li>DOT：持续伤害（灼烧、中毒）；</li>
 *   <li>CONTROL：控制（麻痹、缠绕、沉默）；</li>
 *   <li>DEBUFF：减益（致盲、浸湿、破甲、诅咒）；</li>
 *   <li>BUFF：增益（攻击/防御/速度提升、嘲讽、援护）。</li>
 * </ul>
 */
@Data
@NoArgsConstructor
public class StatusEffectConfig {

    /** 状态唯一 ID，如 BURN。 */
    private String id;

    /** 状态名称。 */
    private String name;

    /** 类别：DOT / CONTROL / DEBUFF / BUFF。 */
    private String category = "DEBUFF";

    /** 描述。 */
    private String description;

    /** 默认持续回合数（技能施加时可覆盖）。 */
    private int defaultDuration = 2;

    /** 每回合持续伤害占最大 HP 的百分比（0 = 无 DOT）。 */
    private double dotPercent = 0;

    /** 速度修正百分比（负数减速，如 -0.30 = 减速 30%）。 */
    private double speedPercent = 0;

    /** 攻击方命中惩罚（如 0.30 = 命中 -30%），作用于携带该状态的攻击方。 */
    private double accuracyPenalty = 0;

    /** 行动跳过概率 [0, 1]（缠绕 = 1.0，麻痹可配置为概率跳过）。 */
    private double skipActionChance = 0;

    /** 是否沉默（沉默时无法使用主动技能）。 */
    private boolean silence = false;

    /** 防御修正百分比（破甲为负数）。 */
    private double defensePercent = 0;

    /** 抗性修正百分比。 */
    private double resistancePercent = 0;

    /** 造成伤害修正百分比（攻击增益为正数）。 */
    private double damageDealtPercent = 0;

    /** 受到伤害修正百分比（诅咒为正数增伤）。 */
    private double damageTakenPercent = 0;

    /** 是否嘲讽（敌方单体技能强制指向携带者）。 */
    private boolean taunt = false;

    /** 援护伤害转移比例（0 = 非援护状态）。 */
    private double guardTransferPercent = 0;
}
