package com.petgame.map.service;

import com.petgame.common.GameRandom;
import com.petgame.config.GameConfigRegistry;
import com.petgame.config.model.RandomEventsConfig;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 随机事件服务（阶段 10，需求 §74）。
 * <p>
 * 探索区域时按概率触发简单选择事件：
 * <ul>
 *   <li>rollRandomEvent：按区域配置随机触发事件（概率配置化）。</li>
 *   <li>resolveEventOption：解析玩家选择，按权重抽取结果。</li>
 * </ul>
 * 结果类型：GIFT_GOLD / GIFT_ITEM / GIFT_MATERIAL / TRIGGER_BATTLE / TRIGGER_CAPTURE / NOTHING
 */
@Service
public class RandomEventService {

    private static final Logger log = LoggerFactory.getLogger(RandomEventService.class);

    /** 随机事件触发概率（每次探索 15%）。 */
    private static final double EVENT_TRIGGER_CHANCE = 0.15;

    private final GameConfigRegistry registry;

    public RandomEventService(GameConfigRegistry registry) {
        this.registry = registry;
    }

    /**
     * 尝试触发随机事件。
     *
     * @param mapId  当前区域 ID
     * @param random 随机源
     * @return 事件视图（null 表示未触发）
     */
    public EventView rollRandomEvent(String mapId, GameRandom random) {
        RandomEventsConfig config = registry.getRandomEventsConfig();
        if (config == null || config.getRandomEvents() == null || config.getRandomEvents().isEmpty()) {
            return null;
        }

        // 按概率判定是否触发
        if (!random.chance(EVENT_TRIGGER_CHANCE)) {
            return null;
        }

        // 筛选当前区域可用的事件
        List<RandomEventsConfig.RandomEventConfig> candidates = new ArrayList<>();
        for (RandomEventsConfig.RandomEventConfig event : config.getRandomEvents()) {
            if (event.getRegionIds() == null || event.getRegionIds().isEmpty()
                    || event.getRegionIds().contains(mapId)) {
                candidates.add(event);
            }
        }
        if (candidates.isEmpty()) {
            return null;
        }

        // 随机选择一个事件
        RandomEventsConfig.RandomEventConfig selected =
                candidates.get(random.nextInt(0, candidates.size() - 1));

        EventView view = new EventView();
        view.setEventId(selected.getId());
        view.setName(selected.getName());
        view.setDescription(selected.getDescription());
        for (RandomEventsConfig.EventOptionConfig opt : selected.getOptions()) {
            EventView.OptionView ov = new EventView.OptionView();
            ov.setOptionId(opt.getId());
            ov.setText(opt.getText());
            view.getOptions().add(ov);
        }

        log.debug("随机事件触发：mapId={}, eventId={}", mapId, selected.getId());
        return view;
    }

    /**
     * 解析玩家选择的事件选项，按权重抽取结果。
     *
     * @param eventId  事件 ID
     * @param optionId 选项 ID
     * @param random   随机源
     * @return 结果视图
     */
    public EventResultView resolveEventOption(String eventId, String optionId, GameRandom random) {
        RandomEventsConfig config = registry.getRandomEventsConfig();
        RandomEventsConfig.RandomEventConfig event = null;
        for (RandomEventsConfig.RandomEventConfig e : config.getRandomEvents()) {
            if (e.getId().equals(eventId)) {
                event = e;
                break;
            }
        }
        if (event == null) {
            throw new com.petgame.common.BusinessException("EVENT_NOT_FOUND", "事件不存在: " + eventId);
        }

        RandomEventsConfig.EventOptionConfig option = null;
        for (RandomEventsConfig.EventOptionConfig opt : event.getOptions()) {
            if (opt.getId().equals(optionId)) {
                option = opt;
                break;
            }
        }
        if (option == null) {
            throw new com.petgame.common.BusinessException("EVENT_OPTION_NOT_FOUND",
                    "事件选项不存在: " + eventId + "/" + optionId);
        }

        // 按权重抽取结果
        RandomEventsConfig.EventOutcomeConfig outcome = rollOutcome(option.getOutcomes(), random);
        EventResultView result = new EventResultView();
        result.setEventId(eventId);
        result.setOptionId(optionId);
        result.setType(outcome.getType());

        switch (outcome.getType()) {
            case "GIFT_GOLD":
                int gold = random.nextInt(outcome.getValueMin(), outcome.getValueMax());
                result.setGoldGained(gold);
                result.setDescription("获得了 " + gold + " 金币！");
                break;
            case "GIFT_ITEM":
            case "GIFT_MATERIAL":
                if (outcome.getItemPool() != null && !outcome.getItemPool().isEmpty()) {
                    String itemId = outcome.getItemPool()
                            .get(random.nextInt(0, outcome.getItemPool().size() - 1));
                    result.setItemId(itemId);
                    var item = registry.getItem(itemId);
                    result.setDescription("获得了 " + (item != null ? item.getName() : itemId) + "！");
                } else {
                    result.setDescription("什么也没有发现...");
                }
                break;
            case "TRIGGER_BATTLE":
            case "TRIGGER_CAPTURE":
                result.setEncounterGroupId(outcome.getEncounterGroupId());
                result.setDescription(outcome.getType().equals("TRIGGER_BATTLE")
                        ? "遭遇了野生宠物！" : "发现了可以捕捉的野生宠物！");
                break;
            case "NOTHING":
            default:
                result.setDescription("什么也没有发生。");
                break;
        }

        log.info("随机事件解决：event={}, option={}, type={}", eventId, optionId, outcome.getType());
        return result;
    }

    /** 按权重抽取结果。 */
    private RandomEventsConfig.EventOutcomeConfig rollOutcome(
            List<RandomEventsConfig.EventOutcomeConfig> outcomes, GameRandom random) {
        int totalWeight = outcomes.stream()
                .mapToInt(RandomEventsConfig.EventOutcomeConfig::getWeight).sum();
        if (totalWeight <= 0) {
            // 全部权重为 0，返回 NOTHING
            RandomEventsConfig.EventOutcomeConfig nothing = new RandomEventsConfig.EventOutcomeConfig();
            nothing.setType("NOTHING");
            return nothing;
        }
        int roll = random.nextInt(1, totalWeight);
        int cumulative = 0;
        for (RandomEventsConfig.EventOutcomeConfig outcome : outcomes) {
            cumulative += outcome.getWeight();
            if (roll <= cumulative) {
                return outcome;
            }
        }
        return outcomes.get(outcomes.size() - 1);
    }

    // ==================== DTO ====================

    @Data
    public static class EventView {
        private String eventId;
        private String name;
        private String description;
        private List<OptionView> options = new ArrayList<>();

        @Data
        public static class OptionView {
            private String optionId;
            private String text;
        }
    }

    @Data
    public static class EventResultView {
        private String eventId;
        private String optionId;
        private String type;
        private String description;
        private int goldGained;
        private String itemId;
        private String encounterGroupId;
    }
}
