package com.petgame.config.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 任务配置（阶段 9）。
 * <p>
 * 数据来源 game-config/quests/quests.yml。
 * 主线任务驱动区域推进直至通关，支线提供轻量目标并可永久改变地图，
 * 隐藏任务未触发前不显示。条件类型为有限枚举，不建通用脚本引擎。
 */
@Data
public class QuestsConfig {

    private int configVersion = 1;

    /** 任务列表（主线 + 支线 + 隐藏）。 */
    private List<QuestConfig> quests = new ArrayList<>();

    /** NPC 对话配置列表。 */
    private List<NpcConfig> npcs = new ArrayList<>();

    /** 新手教学步骤列表。 */
    private List<TutorialStepConfig> tutorials = new ArrayList<>();

    // ==================== 任务配置 ====================

    /** 单个任务配置。 */
    @Data
    public static class QuestConfig {
        /** 任务 ID（如 QUEST_MAIN_01）。 */
        private String id;
        /** 任务名称。 */
        private String name;
        /** 任务类型：MAIN / SIDE / HIDDEN。 */
        private String type;
        /** 任务描述。 */
        private String description;
        /** 关联区域 ID（主线必填，引用 maps.yml region.id）。 */
        private String regionId;
        /** 前置任务 ID（可为 null，表示无前置；支持多前置用逗号分隔）。 */
        private String prerequisiteQuestId;
        /** 隐藏任务标记。 */
        private boolean hidden;
        /** 隐藏任务触发条件（hidden=true 时使用）。 */
        private HiddenTriggerConfig trigger;
        /** 有序目标列表。 */
        private List<ObjectiveConfig> objectives = new ArrayList<>();
        /** 完成奖励。 */
        private RewardConfig rewards;
        /** 永久地图变更（支线专属）。 */
        private List<MapChangeConfig> mapChanges = new ArrayList<>();
        /** 任务完成后解锁的区域 ID（QUEST 类型区域）。 */
        private String unlockRegionId;
    }

    /** 任务目标配置。 */
    @Data
    public static class ObjectiveConfig {
        /** 目标 ID。 */
        private String objectiveId;
        /**
         * 目标类型：DIALOGUE / GATHER / CAPTURE / DEFEAT / DEFEAT_BOSS / ARRIVE。
         */
        private String type;
        /** 目标描述文本。 */
        private String description;
        /** 目标引用 ID（NPC/采集点/宠物种族/Boss/区域）。 */
        private String targetId;
        /** 目标数量（默认 1）。 */
        private int targetCount = 1;
        /** 限定区域（可选，如 DEFEAT 限定在特定区域）。 */
        private String regionId;
    }

    /** 奖励配置。 */
    @Data
    public static class RewardConfig {
        /** 固定奖励列表。 */
        private List<RewardEntry> fixed = new ArrayList<>();
        /** 三选一奖励组列表。 */
        private List<RewardChoiceGroup> choices = new ArrayList<>();
        /** 赠送宠物（可选）。 */
        private GiftPetConfig giftPet;
    }

    /** 单条奖励条目。 */
    @Data
    public static class RewardEntry {
        /** 奖励类型：GOLD / EXP / ITEM / SKILL_BOOK。 */
        private String type;
        /** 道具 ID（type=ITEM/SKILL_BOOK 时必填）。 */
        private String itemId;
        /** 数量（GOLD/EXP 时为数值，ITEM 时为个数）。 */
        private int quantity = 1;
    }

    /** 三选一奖励组。 */
    @Data
    public static class RewardChoiceGroup {
        /** 选择组 ID。 */
        private String choiceId;
        /** 选项列表（通常 3 个）。 */
        private List<RewardEntry> options = new ArrayList<>();
    }

    /** 赠送宠物配置。 */
    @Data
    public static class GiftPetConfig {
        /** 种族 ID（引用 pets.yml）。 */
        private String speciesId;
        /** 赠送等级。 */
        private int level = 5;
        /** 固定高资质（全维度统一值，如 80）。 */
        private int aptitudeAll = 60;
        /** 固定特殊技能 ID 列表。 */
        private List<String> skills = new ArrayList<>();
        /** 来源标记（QUEST_GIFT）。 */
        private String source = "QUEST_GIFT";
    }

    /** 隐藏任务触发条件。 */
    @Data
    public static class HiddenTriggerConfig {
        /** 触发类型：LOCATION / PET / ITEM / DIALOGUE_COUNT。 */
        private String triggerType;
        /** 触发目标 ID（区域 ID / 宠物种族 ID / 道具 ID / NPC ID）。 */
        private String triggerTarget;
        /** 触发次数（DIALOGUE_COUNT 时为对话次数，LOCATION 时为进入次数）。 */
        private int triggerCount = 1;
    }

    /** 永久地图变更配置。 */
    @Data
    public static class MapChangeConfig {
        /** 变更 ID（如 MAP_CHANGE_SHORTCUT_FOREST）。 */
        private String changeId;
        /** 变更类型：OPEN_SHORTCUT / ADD_MERCHANT / REPAIR_ROAD / OPEN_RESTORE_POINT。 */
        private String changeType;
        /** 影响区域 ID。 */
        private String regionId;
        /** 变更描述。 */
        private String description;
        /** Tiled 对象 ID（前端据此渲染变更）。 */
        private String objectId;
    }

    // ==================== NPC 对话配置 ====================

    /** NPC 配置。 */
    @Data
    public static class NpcConfig {
        /** NPC ID（对应 Tiled 对象层 npc 对象）。 */
        private String npcId;
        /** NPC 名称。 */
        private String name;
        /** 所在区域 ID。 */
        private String regionId;
        /** 对话节点列表（线性对话树）。 */
        private List<DialogueNodeConfig> dialogues = new ArrayList<>();
    }

    /** 对话节点配置。 */
    @Data
    public static class DialogueNodeConfig {
        /** 节点 ID。 */
        private String nodeId;
        /** 对话文本。 */
        private String text;
        /** 下一节点 ID（null 表示对话结束）。 */
        private String nextNode;
    }

    // ==================== 新手教学配置 ====================

    /** 教学步骤配置。 */
    @Data
    public static class TutorialStepConfig {
        /** 步骤 ID。 */
        private String stepId;
        /** 步骤名称。 */
        private String name;
        /** 步骤描述。 */
        private String description;
        /** 触发类型：ARRIVE / DEFEAT / CAPTURE 等（与任务目标同枚举）。 */
        private String triggerType;
        /** 触发目标 ID（* 表示任意匹配）。 */
        private String triggerTarget;
        /** 排序序号（从小到大）。 */
        private int order;
        /** 是否可跳过。 */
        private boolean skippable = true;
        /** 完成奖励（如捕捉教学赠送技能书）。 */
        private List<RewardEntry> rewards = new ArrayList<>();
    }
}
