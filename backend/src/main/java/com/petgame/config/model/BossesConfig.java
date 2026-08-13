package com.petgame.config.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Boss 配置（阶段 7）。
 * <p>
 * 数据来源 game-config/bosses/bosses.yml。
 * 每个 Boss 含 3 个难度（NORMAL/HARD/NIGHTMARE），
 * 每难度含 stats/skills/passives/phases/drops/luckGain。
 */
@Data
public class BossesConfig {

    private int configVersion = 1;

    private List<BossConfig> bosses = new ArrayList<>();

    /** 单个 Boss 配置。 */
    @Data
    public static class BossConfig {
        /** Boss ID（如 BOSS_MEADOW_GUARDIAN）。 */
        private String id;
        /** Boss 名称。 */
        private String name;
        /** 所属区域 ID（引用 maps.yml region.id）。 */
        private String mapId;
        /** 属性 ID（引用 elements.yml）。 */
        private String element;
        /** 推荐等级。 */
        private int recommendedLevel;
        /** 难度配置：key = NORMAL/HARD/NIGHTMARE。 */
        private Map<String, DifficultyConfig> difficulties = new LinkedHashMap<>();
    }

    /** 单个难度配置。 */
    @Data
    public static class DifficultyConfig {
        /** Boss 六维属性。 */
        private StatsConfig stats = new StatsConfig();
        /** 技能 ID 列表（引用 skills.yml）。 */
        private List<String> skills = new ArrayList<>();
        /** 被动 ID 列表（引用 skills.yml passives）。 */
        private List<String> passives = new ArrayList<>();
        /** 击败后获得的幸运值。 */
        private int luckGain = 4;
        /** 阶段触发器列表（Boss 专用）。 */
        private List<PhaseTrigger> phases = new ArrayList<>();
        /** 掉落配置：key = COMMON/RARE/EPIC/LEGENDARY。 */
        private Map<String, List<DropEntry>> drops = new LinkedHashMap<>();
    }

    /** Boss 六维属性。 */
    @Data
    public static class StatsConfig {
        private int maxHp;
        private int strength;
        private int spirit;
        private int defense;
        private int resistance;
        private int speed;
    }

    /** 阶段触发器：Boss HP 低于阈值时触发效果。 */
    @Data
    public static class PhaseTrigger {
        /** HP 百分比阈值（如 0.50 = 50%）。 */
        private double hpPercent;
        /** 触发效果列表。 */
        private List<PhaseEffect> effects = new ArrayList<>();
    }

    /** 阶段效果。 */
    @Data
    public static class PhaseEffect {
        /** 效果类型：ADD_SKILL / ADD_SHIELD / BUFF_SELF。 */
        private String type;
        /** 技能 ID（ADD_SKILL 时必填）。 */
        private String skillId;
        /** 护盾值（ADD_SHIELD 时必填）。 */
        private int shieldValue;
        /** 状态 ID（BUFF_SELF 时必填）。 */
        private String statusId;
    }

    /** 掉落条目。 */
    @Data
    public static class DropEntry {
        /** 道具 ID（引用 items.yml）。 */
        private String itemId;
        /** 掉落数量。 */
        private int qty = 1;
        /** 掉落概率（0.0~1.0）。 */
        private double chance = 1.0;
        /** 幸运兑换时的数量（低价值多数量、高价值少数量）。 */
        private int exchangeQty = 1;
    }
}
