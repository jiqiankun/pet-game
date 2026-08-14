package com.petgame.config.model;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 道具配置（items/*.yml）。
 * <p>
 * 阶段 4 实现恢复道具基础（需求 §92/§93）。背包按 category 分类，不限容量。
 * 玩家存档只保存道具 ID 与数量，不复制配置内容（数据分离原则）。
 */
@Data
@NoArgsConstructor
public class ItemConfig {

    /** 道具 ID（如 ITEM_POTION_SMALL）。 */
    private String id;

    /** 名称。 */
    private String name;

    /** 描述。 */
    private String description;

    /** 背包分类（需求 §93）：CAPTURE 捕捉 / RECOVERY 恢复 / MATERIAL 材料 / SKILL_BOOK 技能书 / KEY_ITEM 重要物品。 */
    private String category;

    /** 道具类型（决定使用效果）：HEAL_HP 恢复HP / REVIVE 复苏 / CAPTURE_BALL 捕捉球 / MATERIAL 材料 / SKILL_BOOK 技能书 / KEY_ITEM 重要物品。 */
    private String itemType;

    /** 效果数值（HEAL_HP=恢复HP量；REVIVE=复活后恢复HP百分比；捕捉球=捕获倍率）。 */
    private double value;

    /** 是否可在战斗外使用。 */
    private boolean usableOutsideBattle = false;

    /** 是否可在战斗内使用。 */
    private boolean usableInBattle = false;

    /** 是否可丢弃（重要物品不可丢弃）。 */
    private boolean discardable = true;

    /** 技能书引用的技能 ID（仅 SKILL_BOOK 类型使用，引用 skills.yml）。 */
    private String skillId;

    /** 商店售价（0 或未设置表示不可在商店购买）。 */
    private int price = 0;

    /** 技能书学习限制（仅 SKILL_BOOK 类型使用，阶段 10）。 */
    private SkillBookRestriction skillBookRestriction;

    /**
     * 技能书学习限制配置。
     * <p>
     * 为空或 null 表示不限制（所有宠物可学，如留生一击等通用技能）。
     * 非空时按 elements/rarities/speciesIds/excludeSpeciesIds 联合过滤。
     */
    @lombok.Data
    @lombok.NoArgsConstructor
    public static class SkillBookRestriction {

        /** 允许学习的宠物属性列表（为空表示不限属性）。 */
        private java.util.List<String> elements;

        /** 允许学习的稀有度列表（COMMON/RARE/EPIC/LEGENDARY，为空表示不限）。 */
        private java.util.List<String> rarities;

        /** 明确允许学习的种族 ID 列表（为空表示不限，仅排除列表生效）。 */
        private java.util.List<String> speciesIds;

        /** 明确排除的种族 ID 列表（专属技能保护，不可通过技能书学习）。 */
        private java.util.List<String> excludeSpeciesIds;
    }
}
