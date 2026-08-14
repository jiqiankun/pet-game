package com.petgame.config.model;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 随机事件配置模型（阶段 10）。
 * <p>
 * 对应 events/random-events.yml，定义探索中随机触发的事件。
 * 每个事件包含描述与多个选项，选项下按权重抽取结果。
 */
@Data
@NoArgsConstructor
public class RandomEventsConfig {

    /** 随机事件列表。 */
    private List<RandomEventConfig> randomEvents = new ArrayList<>();

    /**
     * 单个随机事件配置。
     */
    @Data
    @NoArgsConstructor
    public static class RandomEventConfig {

        /** 事件唯一 ID（如 EVENT_INJURED_PET）。 */
        private String id;

        /** 事件名称。 */
        private String name;

        /** 事件描述文本。 */
        private String description;

        /** 可触发的区域 ID 列表（为空表示所有区域可触发）。 */
        private List<String> regionIds = new ArrayList<>();

        /** 事件选项列表。 */
        private List<EventOptionConfig> options = new ArrayList<>();
    }

    /**
     * 事件选项配置。
     */
    @Data
    @NoArgsConstructor
    public static class EventOptionConfig {

        /** 选项 ID（如 HEAL / LEAVE / APPROACH）。 */
        private String id;

        /** 选项显示文本。 */
        private String text;

        /** 可能的结果列表（按权重抽取）。 */
        private List<EventOutcomeConfig> outcomes = new ArrayList<>();
    }

    /**
     * 事件结果配置。
     */
    @Data
    @NoArgsConstructor
    public static class EventOutcomeConfig {

        /** 结果类型：GIFT_GOLD / GIFT_ITEM / GIFT_MATERIAL / TRIGGER_BATTLE / TRIGGER_CAPTURE / NOTHING。 */
        private String type;

        /** 权重（用于加权随机抽取）。 */
        private int weight = 1;

        /** 金币数量下限（GIFT_GOLD 类型使用）。 */
        private int valueMin = 0;

        /** 金币数量上限（GIFT_GOLD 类型使用）。 */
        private int valueMax = 0;

        /** 道具池（GIFT_ITEM / GIFT_MATERIAL 类型使用，从中随机抽取 1 个）。 */
        private List<String> itemPool = new ArrayList<>();

        /** 遭遇组 ID（TRIGGER_BATTLE / TRIGGER_CAPTURE 类型使用）。 */
        private String encounterGroupId;
    }
}
