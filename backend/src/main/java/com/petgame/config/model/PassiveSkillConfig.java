package com.petgame.config.model;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 被动技能配置模型（阶段 3 框架）。
 * <p>
 * 被动尽量采用机制型设计，配置驱动。引擎按 trigger + effectType 统一解释，
 * 不针对具体被动 ID 写分支。
 * <p>
 * 触发时机：ON_ENTER（登场）/ ON_EXIT（退场）/ ON_HIT_TAKEN（受击）/
 * ON_ATTACK（攻击）/ ON_CRIT（暴击）/ ON_KILL（击败）/ ON_DEATH（倒下）/
 * ON_ROUND_START（回合开始）/ ON_ROUND_END（回合结束）。
 */
@Data
@NoArgsConstructor
public class PassiveSkillConfig {

    /** 被动唯一 ID，如 PASSIVE_UNYIELDING。 */
    private String id;

    /** 被动名称。 */
    private String name;

    /** 描述。 */
    private String description;

    /** 触发时机（见类注释枚举）。 */
    private String trigger;

    /**
     * 效果类型：
     * <ul>
     *   <li>SURVIVE_LETHAL：第一次受到致命伤害时保留 1 点生命（不屈）；</li>
     *   <li>APPLY_STATUS_ALLY_ALL：触发时给己方全体施加状态（顺风：登场全队加速）；</li>
     *   <li>APPLY_STATUS_SELF：触发时给自身施加状态；</li>
     *   <li>DAMAGE_ENEMY_RANDOM：触发时对敌方随机目标造成伤害（余烬：倒下时火伤）；</li>
     *   <li>HEAL_SELF：触发时治疗自身。</li>
     * </ul>
     */
    private String effectType;

    /** 效果引用的状态 ID（APPLY_STATUS_* 类型使用）。 */
    private String statusId;

    /** 效果施加属性（DAMAGE_ENEMY_RANDOM 类型使用，如 FIRE）。 */
    private String element;

    /** 效果基础数值。 */
    private double value = 0;

    /** 灵力系数（效果数值 = value + spiritScale × 灵力）。 */
    private double spiritScale = 0;

    /** 每场战斗最大触发次数（0 = 不限）。 */
    private int maxTriggerPerBattle = 0;
}
