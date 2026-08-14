package com.petgame.config.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 成就配置（阶段 11）。
 * <p>
 * 数据来源 game-config/achievements/achievements.yml。
 * 成就采用事件记录驱动（不构建成就依赖树/规则引擎）：每个成就有独立的
 * conditionType/conditionValue/conditionCount，由 AchievementService 在既有
 * 结算点事件触发时按条件类型从玩家状态快照计算是否达成。
 * 奖励为金币/经验/捕捉球/材料/称号/头像/徽章，不增加永久大量战斗属性（需求 §110）。
 */
@Data
public class AchievementsConfig {

    private int configVersion = 1;

    /** 成就列表。 */
    private List<AchievementConfig> achievements = new ArrayList<>();

    /** 单个成就配置。 */
    @Data
    public static class AchievementConfig {
        /** 成就 ID（如 ACH_CAPTURE_10）。 */
        private String id;
        /** 成就名称。 */
        private String name;
        /** 成就描述。 */
        private String description;
        /**
         * 分类：EXPLORE(探索) / CAPTURE(捕捉) / BREED(培养) /
         * BATTLE(战斗) / BOSS / POKEDEX(图鉴) / SPECIAL(特殊)。
         */
        private String category;
        /**
         * 条件类型（有限枚举，不建通用脚本引擎）：
         * <ul>
         *   <li>STAT_GE：statValue 统计项 ≥ conditionCount</li>
         *   <li>CAPTURE_SPECIES_COUNT：已捕获种族数 ≥ conditionCount</li>
         *   <li>DISCOVER_SPECIES_COUNT：已发现种族数 ≥ conditionCount</li>
         *   <li>CAPTURE_SPECIFIC：已捕获指定种族（conditionValue）</li>
         *   <li>DISCOVER_SPECIFIC：已发现指定种族（conditionValue）</li>
         *   <li>REGION_UNLOCK_COUNT：已解锁区域数 ≥ conditionCount</li>
         *   <li>REGION_UNLOCK_SPECIFIC：已解锁指定区域（conditionValue）</li>
         *   <li>BOSS_DEFEAT_COUNT：累计击败 Boss 数 ≥ conditionCount</li>
         *   <li>BOSS_DEFEAT_ALL_MAIN：全部主 Boss 普通难度首通</li>
         *   <li>BOSS_CHALLENGE_COUNT：累计完成 Boss 挑战目标数 ≥ conditionCount</li>
         *   <li>POKEDEX_RESEARCH_LEVEL：指定种族（conditionValue）研究等级 ≥ conditionCount</li>
         *   <li>QUEST_COMPLETE_COUNT：已完成任务数 ≥ conditionCount</li>
         *   <li>MAIN_QUEST_COMPLETE：主线通关（storyCompleted）</li>
         *   <li>PET_LEVEL_SPECIFIC：拥有指定种族（conditionValue）宠物等级 ≥ conditionCount</li>
         *   <li>PET_LEVEL_MAX：拥有任意 Lv.50 宠物</li>
         *   <li>SPECIAL_APPEARANCE_CAPTURE：捕获过特殊外观个体</li>
         *   <li>ELITE_CAPTURE：捕获过精英个体</li>
         *   <li>GOLD_GE、EXP_POOL_GE：金币/经验池 ≥ conditionCount</li>
         * </ul>
         */
        private String conditionType;
        /** 条件参考值（如统计项 key / 种族 ID / 区域 ID / 研究等级目标）。 */
        private String conditionValue;
        /** 条件数量阈值（默认 1）。 */
        private int conditionCount = 1;
        /** 达成奖励列表。 */
        private List<RewardEntry> rewards = new ArrayList<>();
        /** 授予的称号 ID（可选，展示型奖励）。 */
        private String titleId;
        /** 授予的头像 ID（可选，展示型奖励）。 */
        private String avatarId;
        /** 隐藏成就（未达成前不显示）。 */
        private boolean hidden;
    }

    /** 单条奖励条目。 */
    @Data
    public static class RewardEntry {
        /** 奖励类型：GOLD / EXP / ITEM。 */
        private String type;
        /** 道具 ID（type=ITEM 时必填，如捕捉球/材料）。 */
        private String itemId;
        /** 数量。 */
        private int quantity = 1;
    }
}