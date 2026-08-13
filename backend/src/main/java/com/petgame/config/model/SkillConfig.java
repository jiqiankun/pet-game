package com.petgame.config.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 技能配置模型（阶段 3，修订 REV-001）。
 * <p>
 * 对应 skills/skills.yml 中的单个技能条目。配置驱动，引擎代码不针对具体技能 ID 写分支。
 * <p>
 * 技能按「来源 × 类型」两个独立维度建模（需求 §23、技术方案 §25/§75）：
 * source（INNATE/BOOK，预留 EVOLUTION/SPECIAL）× skillType（ACTIVE/PASSIVE），
 * 不使用单一字段同时承担两个概念。
 * <p>
 * 效果 = 基础值 + 多个属性系数（SkillScaling，支持 maxOf 取两属性较高者），支持多效果组合与蓄力。
 * 同一个 SkillDefinition 可被多个宠物种族引用（技能共享，需求 §149）。
 */
@Data
@NoArgsConstructor
public class SkillConfig {

    /** 技能唯一 ID，如 SKILL_FIRE_CLAW。 */
    private String id;

    /** 技能名称。 */
    private String name;

    /** 技能描述。 */
    private String description;

    /** 技能属性（9 属性之一），决定克制与本属性加成。 */
    private String element;

    /** 稀有度：NORMAL / RARE / EXCLUSIVE。 */
    private String rarity = "NORMAL";

    /** 技能来源：INNATE（自身）/ BOOK（技能书），预留 EVOLUTION / SPECIAL（需求 §23）。 */
    private String source = "INNATE";

    /** 技能类型：ACTIVE（主动）/ PASSIVE（被动）（需求 §23）。 */
    private String skillType = "ACTIVE";

    /** AI 语义标签（阶段 10 自动战斗消费）：DAMAGE/HEAL/CONTROL/CAPTURE_ASSIST/SURVIVAL/FINISHER/SHIELD_BREAK/DISPEL/SWITCH/ACTION_ORDER/LIFE_STEAL。 */
    private List<String> tags = new ArrayList<>();

    /** 每场战斗最大成功使用次数（0 = 不限制，技术方案 §75 预留，第一阶段默认不启用）。 */
    private int maxUsesPerBattle = 0;

    /** 伤害类型：PHYSICAL（受防御减伤）/ MAGICAL（受抗性减伤）/ NONE（无直接数值）。 */
    private String damageType = "NONE";

    /** 主效果类型：DAMAGE / HEAL / SHIELD / NONE（仅附加效果）。 */
    private String effectType = "DAMAGE";

    /** 目标类型：ENEMY_SINGLE / ENEMY_ALL / ALLY_SINGLE / ALLY_ALL / SELF。 */
    private String target = "ENEMY_SINGLE";

    /** 基础数值。 */
    private double baseValue = 0;

    /** 属性系数：stat（HP/STRENGTH/SPIRIT/DEFENSE/RESISTANCE/SPEED）→ 系数。 */
    private Map<String, Double> scaling = new LinkedHashMap<>();

    /** maxOf 系数参与属性列表（取其中较高者，如 [strength, spirit]，技术方案 §25）。 */
    private List<String> maxOf = new ArrayList<>();

    /** maxOf 系数（与 maxOf 配套：基础值 += maxOfCoefficient × MAX(maxOf 各属性)）。 */
    private double maxOfCoefficient = 0;

    /** 冷却回合数（0 = 无冷却），每宠独立计算。 */
    private int cooldown = 0;

    /** 命中率 [0, 1]。 */
    private double accuracy = 1.0;

    /** 蓄力回合数（0 = 瞬发，1 = 本回合蓄力、下回合释放）。 */
    private int chargeTurns = 0;

    /** 附加效果列表（多效果组合）。 */
    private List<SkillEffectConfig> effects = new ArrayList<>();

    /**
     * 技能附加效果配置（Effect 组合框架，技术方案 §76）。
     * <p>
     * 支持的效果类型：
     * APPLY_STATUS / DAMAGE / HEAL / SHIELD / LIFE_STEAL / LEAVE_AT_ONE_HP /
     * REMOVE_STATUS / DISPEL / STEAL_BUFF / HP_PERCENT_EXCHANGE / SWITCH_PET /
     * CHANGE_ACTION_ORDER / MODIFY_COOLDOWN / DELAYED / STACK / LIFE_COST / PROTECT_FROM_DEFEAT。
     */
    @Data
    @NoArgsConstructor
    public static class SkillEffectConfig {

        /** 效果类型（见类注释）。 */
        private String type;

        /** APPLY_STATUS / REMOVE_STATUS / STACK 引用的状态 ID。 */
        private String statusId;

        /** 触发概率 [0, 1]，默认必中。 */
        private double chance = 1.0;

        /** 数值效果的基础值（MODIFY_COOLDOWN 时为冷却变化量，负数=减少）。 */
        private double value = 0;

        /** 数值效果的属性系数。 */
        private Map<String, Double> scaling = new LinkedHashMap<>();

        /** 百分比参数：LIFE_STEAL 吸血比例 / HP_PERCENT_EXCHANGE 交换比例 / LIFE_COST 生命代价比例。 */
        private double percent = 0;

        /** REMOVE_STATUS / DISPEL 的状态类别过滤（如 [DOT]、[BUFF]）；为空时 DISPEL 默认移除全部 BUFF。 */
        private List<String> categories = new ArrayList<>();

        /** REMOVE_STATUS 仅移除持续伤害状态（dotPercent > 0，留生一击清 DOT 专用，需求 §142.3）。 */
        private boolean dotOnly = false;

        /** 仅在 LEAVE_AT_ONE_HP 触发保护（致死被保留 1HP）时生效（留生一击清 DOT/附加震慑，需求 §142）。 */
        private boolean onProtect = false;

        /** 仅对可捕捉目标生效（震慑联动，需求 §142；Boss 等不可捕捉目标不触发）。 */
        private boolean capturableOnly = false;

        /** DELAYED 效果的延迟回合数。 */
        private int delayRounds = 0;
    }
}
