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

    /** 逃跑成功。 */
    FLEE_SUCCESS,

    /** 逃跑失败。 */
    FLEE_FAIL,

    /** 行动被跳过（控制状态）。 */
    ACTION_SKIPPED,

    /** 回合结束。 */
    TURN_ENDED,

    /** 战斗结束。 */
    BATTLE_ENDED
}
