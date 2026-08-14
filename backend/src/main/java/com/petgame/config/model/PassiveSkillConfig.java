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

    /** 每场战斗最大触发次数（0 = 不限，即 oncePerBattle 语义由 maxTriggerPerBattle=1 实现）。 */
    private int maxTriggerPerBattle = 0;

    /** 每回合最多触发一次（REV-009 防递归，技术方案 §78）。 */
    private boolean oncePerTurn = false;

    /** 每次行动最多触发一次（REV-009 防递归，技术方案 §78）。 */
    private boolean oncePerAction = false;

    // ---- 被动技能体系重构（阶段 14 补充：被动来源与叠加规则）----

    /**
     * 被动来源（概念归属，配合实际生效来源使用）：
     * INNATE 天生 / LEVEL_UP 升级 / EVOLUTION 进化 / SKILL_BOOK 技能书。
     * <p>
     * 配置层用于标注「本被动默认归属」；实际生效来源仍由 species.passives（固有）
     * 与 player_pet_skill(sourceType=SKILL_BOOK)（技能书）决定，二者不冲突。
     */
    private String sourceType;

    /**
     * 效果组（归一化「不同名字但效果相近」的被动，用于叠加/去重）：
     * HP_BONUS / ATTACK_BONUS / DEFENSE_BONUS / SPEED_BONUS / RESIST_BONUS /
     * DAMAGE_REDUCTION / STATUS_RESIST / HEAL_BONUS / SHIELD_BONUS / ON_KILL_ATK / ...
     * 为空表示无组归属（不参与跨被动去重）。
     */
    private String effectGroup;

    /**
     * 叠加规则（仅同 effectGroup 内生效）：
     * UNIQUE 同组只允许一个 / HIGHEST_ONLY 只取最高效果 / ADDITIVE 允许加算 / LIMITED 有限叠加。
     * 默认 HIGHEST_ONLY（同名/同组不双倍）。
     */
    private String stackRule = "HIGHEST_ONLY";

    /** 同组叠加上限（stackRule=LIMITED 或 ADDITIVE 时使用；0 表示无显式上限）。 */
    private int maxStack = 0;

    /** 同组冲突时优先级（数值大者胜出；UNIQUE/HIGHEST_ONLY 时高优先级保留）。 */
    private int priority = 0;
}
