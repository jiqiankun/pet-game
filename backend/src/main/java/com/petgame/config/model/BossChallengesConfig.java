package com.petgame.config.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Boss 挑战目标配置（阶段 11）。
 * <p>
 * 数据来源 game-config/bosses/boss-challenges.yml。
 * 每个主要 Boss 设 4 个挑战目标（决策四 §1147）：指定回合内击败、不使用恢复道具、
 * 无宠物失去战斗能力、使用 3 种不同属性宠物取胜；仅在击败场次中判定，任意难度均可计入。
 * 单目标首次完成发放成就 + 一次性实用奖励；集齐某 Boss 全部 4 目标授予该 Boss 专属称号。
 * 隐藏/精英 Boss 不设挑战目标。
 */
@Data
public class BossChallengesConfig {

    private int configVersion = 1;

    /** 各 Boss 挑战目标组列表（仅主 Boss）。 */
    private List<BossChallengeGroup> groups = new ArrayList<>();

    /** 单个 Boss 的挑战目标组。 */
    @Data
    public static class BossChallengeGroup {
        /** Boss ID（引用 bosses.yml）。 */
        private String bossId;
        /** 该 Boss 的挑战目标列表（通常 4 个）。 */
        private List<ChallengeConfig> challenges = new ArrayList<>();
        /** 集齐全部挑战后授予的专属称号 ID（可选，展示型奖励）。 */
        private String completionTitleId;
    }

    /** 单个挑战目标配置。 */
    @Data
    public static class ChallengeConfig {
        /** 挑战目标 ID（如 CHALLENGE_TURN_LIMIT）。 */
        private String challengeId;
        /**
         * 目标类型：
         * TURN_LIMIT(指定回合内击败) / NO_RECOVERY_ITEM(不使用恢复道具) /
         * NO_PET_FAINTED(无宠物失去战斗能力) / MULTI_ELEMENT(使用多种不同属性宠物)。
         */
        private String type;
        /** 目标名称。 */
        private String name;
        /** 目标描述。 */
        private String description;
        /** 目标参数（TURN_LIMIT=回合上限；MULTI_ELEMENT=不同属性数，默认 3）。 */
        private int value = 1;
        /** 一次性实用奖励列表。 */
        private List<RewardEntry> rewards = new ArrayList<>();
        /** 首次完成授予的成就 ID（可选）。 */
        private String achievementId;
    }

    /** 单条奖励条目。 */
    @Data
    public static class RewardEntry {
        /** 奖励类型：GOLD / EXP / ITEM。 */
        private String type;
        /** 道具 ID（type=ITEM 时必填）。 */
        private String itemId;
        /** 数量。 */
        private int quantity = 1;
    }
}