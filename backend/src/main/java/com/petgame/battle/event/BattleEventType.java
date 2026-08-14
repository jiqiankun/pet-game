package com.petgame.battle.event;

/**
 * 战斗事件类型（技术方案 §22）。
 * <p>
 * 标准化事件类型，前端仅根据事件播放表现，后端不返回具体动画指令。
 */
public enum BattleEventType {

    /** 回合开始。 */
    TURN_STARTED,

    /** 行动顺序（本回合行动序列）。 */
    ACTION_ORDER,

    /** 某单位开始行动。 */
    ACTION_STARTED,

    /** 技能释放。 */
    SKILL_CAST,

    /** 蓄力开始。 */
    CHARGING,

    /** 伤害。 */
    DAMAGE,

    /** 未命中。 */
    MISS,

    /** 治疗。 */
    HEAL,

    /** 护盾创建。 */
    SHIELD_CREATED,

    /** 增益施加。 */
    BUFF_APPLIED,

    /** 减益施加。 */
    DEBUFF_APPLIED,

    /** 异常状态施加。 */
    STATUS_APPLIED,

    /** 状态持续结算（DOT 等）。 */
    STATUS_TICK,

    /** 状态到期移除。 */
    STATUS_EXPIRED,

    /** 被动技能触发。 */
    PASSIVE_TRIGGERED,

    /** 暴击（伴随 DAMAGE 事件发出）。 */
    CRITICAL,

    /** 宠物倒下。 */
    PET_DEFEATED,

    /** 候补补位。 */
    PET_REPLACED,

    /** 主动换宠。 */
    PET_SWITCHED,

    /** 防御。 */
    DEFEND,

    /** 捕捉尝试（携带捕捉率）。 */
    CAPTURE_ATTEMPT,

    /** 捕捉成功。 */
    CAPTURE_SUCCESS,

    /** 捕捉失败。 */
    CAPTURE_FAIL,

    /** 使用道具（阶段 10 自动战斗：恢复/复苏，仅自动 AI 在开关开启时使用）。 */
    ITEM_USED,

    /** 逃跑成功。 */
    FLEE_SUCCESS,

    /** 逃跑失败。 */
    FLEE_FAIL,

    /** 行动被跳过（控制状态）。 */
    ACTION_SKIPPED,

    // ---- 新增事件（REV-010，技术方案 §79）----

    /** 吸血恢复。 */
    LIFE_STEAL,

    /** 状态被主动移除/驱散。 */
    STATUS_REMOVED,

    /** 捕获震慑附加（安全捕捉窗口）。 */
    STUNNED,

    /** 混乱导致目标改变。 */
    CONFUSED_TARGET_CHANGED,

    /** 反击触发。 */
    COUNTER_TRIGGERED,

    /** Buff 被偷取。 */
    BUFF_STOLEN,

    /** 护盾被击破。 */
    SHIELD_BROKEN,

    /** 行动顺序被干预。 */
    ACTION_ORDER_CHANGED,

    /** 宠物被强制换下/换宠技能触发。 */
    PET_FORCED_SWITCH,

    /** HP 百分比交换（命运天平类，非伤害）。 */
    HP_PERCENT_EXCHANGED,

    /** 延迟效果触发。 */
    DELAYED_EFFECT_TRIGGERED,

    /** 标记叠层变化。 */
    MARK_STACK_CHANGED,

    /** Boss 阶段转换（阶段 7）。 */
    PHASE_TRANSITION,

    /** 控制抗性/衰减生效（阶段 7）。 */
    CONTROL_RESISTED,

    /** 回合结束。 */
    TURN_ENDED,

    /** 战斗结束。 */
    BATTLE_ENDED
}
