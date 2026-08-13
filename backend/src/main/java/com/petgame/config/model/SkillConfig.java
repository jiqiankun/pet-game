package com.petgame.config.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 技能配置模型（阶段 3）。
 * <p>
 * 对应 skills/skills.yml 中的单个技能条目。配置驱动，引擎代码不针对具体技能 ID 写分支。
 * <p>
 * 效果 = 基础值 + 多个属性系数（SkillScaling），支持多效果组合与蓄力。
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

    /** 技能类别：ACTIVE（第一阶段仅主动技能进入引擎）。 */
    private String category = "ACTIVE";

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

    /** 冷却回合数（0 = 无冷却），每宠独立计算。 */
    private int cooldown = 0;

    /** 命中率 [0, 1]。 */
    private double accuracy = 1.0;

    /** 蓄力回合数（0 = 瞬发，1 = 本回合蓄力、下回合释放）。 */
    private int chargeTurns = 0;

    /** 附加效果列表（多效果组合）。 */
    private List<SkillEffectConfig> effects = new ArrayList<>();

    /**
     * 技能附加效果配置。
     * <p>
     * type = APPLY_STATUS：按概率施加状态/Buff/Debuff（由 statusId 引用状态配置）。
     * type = DAMAGE / HEAL / SHIELD：追加一段独立数值效果。
     */
    @Data
    @NoArgsConstructor
    public static class SkillEffectConfig {

        /** 效果类型：APPLY_STATUS / DAMAGE / HEAL / SHIELD。 */
        private String type;

        /** APPLY_STATUS 时引用的状态 ID。 */
        private String statusId;

        /** 触发概率 [0, 1]，默认必中。 */
        private double chance = 1.0;

        /** 数值效果的基础值。 */
        private double value = 0;

        /** 数值效果的属性系数。 */
        private Map<String, Double> scaling = new LinkedHashMap<>();
    }
}
