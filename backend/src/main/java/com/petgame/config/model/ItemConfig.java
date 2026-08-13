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
}
