package com.petgame.config.model;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 状态配置模型（阶段 3，修订 REV-002）。
 * <p>
 * 对应 statuses/statuses.yml 中的单个状态条目。状态效果全部配置化，
 * 引擎按字段统一解释，不针对具体状态 ID 写分支。
 * <p>
 * 状态五类模型（需求 §148.2、技术方案 §77）：
 * <ul>
 *   <li>CONTINUOUS：持续状态（灼烧、中毒、混乱、隐匿、狂暴、再生等）；</li>
 *   <li>BUFF：增益（攻击/防御/速度提升、嘲讽、援护）；</li>
 *   <li>DEBUFF：减益（致盲、浸湿、破甲、诅咒、禁疗）；</li>
 *   <li>SPECIAL_CONTROL：特殊控制（麻痹、缠绕、沉默、震慑）；</li>
 *   <li>MARK：标记（雷印、猎杀印记等，支持叠层）。</li>
 * </ul>
 */
@Data
@NoArgsConstructor
public class StatusEffectConfig {

    /** 状态唯一 ID，如 BURN。 */
    private String id;

    /** 状态名称。 */
    private String name;

    /** 类别：CONTINUOUS / BUFF / DEBUFF / SPECIAL_CONTROL / MARK。 */
    private String category = "DEBUFF";

    /** 描述。 */
    private String description;

    /** 默认持续回合数（技能施加时可覆盖）。 */
    private int defaultDuration = 2;

    /** 每回合持续伤害占最大 HP 的百分比（0 = 无 DOT）。 */
    private double dotPercent = 0;

    // ---- 叠层支持（REV-002，需求 §145 标记叠层）----

    /** 是否可叠层。 */
    private boolean stack = false;

    /** 最大层数（可叠层时生效，默认 1）。 */
    private int maxStack = 1;

    /** 达到最大层数时的触发效果：DAMAGE（对目标造成伤害）/ NONE，数值由 stackTriggerValue 控制。 */
    private String stackTrigger = "NONE";

    /** 叠层触发数值（stackTrigger=DAMAGE 时为基础伤害，随层数结算后清空层数）。 */
    private double stackTriggerValue = 0;

    // ---- 捕捉加成（REV-002/REV-015）----

    /**
     * 是否计入捕捉异常加成。null = 按类别推导（DEBUFF/SPECIAL_CONTROL/CONTINUOUS 计入，
     * BUFF/MARK 不计入）；震慑等状态可显式置 false（需求 §142：震慑不提高捕获率）。
     */
    private Boolean captureBonus = null;

    // ---- 新机制行为字段（REV-002/REV-008，需求 §144）----

    /** 是否混乱（单体技能随机改变目标，群体/自身技能不受影响）。 */
    private boolean confusion = false;

    /** 是否隐匿（单体技能无法选中，群体仍命中，攻击后解除）。 */
    private boolean stealth = false;

    /** 再生：每回合结束恢复占最大 HP 的百分比（0 = 无）。 */
    private double healPercent = 0;

    /** 是否禁疗（无法被治疗效果恢复 HP）。 */
    private boolean healBlock = false;

    /** 反击：受到单体攻击后反击的概率 [0, 1]（0 = 无反击）。 */
    private double counterRate = 0;

    /** 反击伤害 = 基础值 + counterScaling × 力量（反击不触发反击）。 */
    private double counterValue = 0;

    /** 反击力量系数。 */
    private double counterScaling = 0;

    /** 是否捕获震慑（需求 §142：目标至少失去下一次主动行动，提供安全捕捉窗口；不计捕捉加成、不因再次命中 1HP 目标刷新）。 */
    private boolean captureStun = false;

    /** 因本状态跳过行动时立即消耗移除（震慑专用：确保恰好失去下一次主动行动）。 */
    private boolean consumeOnSkip = false;

    /** 是否可被驱散/偷取（默认 true；Boss 阶段 Buff/剧情 Buff 可置 false，技术方案 §76）。 */
    private boolean dispellable = true;

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

    /** 受到物理伤害额外修正百分比（猎杀印记等标记类，需求 §144.6，正数增伤）。 */
    private double physicalTakenPercent = 0;

    /** 是否嘲讽（敌方单体技能强制指向携带者）。 */
    private boolean taunt = false;

    /** 援护伤害转移比例（0 = 非援护状态）。 */
    private double guardTransferPercent = 0;

    /**
     * 捕捉加成判定：显式配置优先，否则按类别推导
     * （DEBUFF/SPECIAL_CONTROL/CONTINUOUS 计入，BUFF/MARK 不计入）。
     */
    public boolean isCaptureBonus() {
        if (captureBonus != null) {
            return captureBonus;
        }
        return "DEBUFF".equals(category) || "SPECIAL_CONTROL".equals(category)
                || "CONTINUOUS".equals(category);
    }
}
