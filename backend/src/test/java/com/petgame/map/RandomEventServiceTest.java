package com.petgame.map;

import com.petgame.common.GameRandom;
import com.petgame.config.GameConfigRegistry;
import com.petgame.config.model.ItemConfig;
import com.petgame.config.model.RandomEventsConfig;
import com.petgame.map.service.RandomEventService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 随机事件服务测试（阶段 10）。
 * <p>
 * 验证：事件触发、选项解析、结果抽取。
 */
class RandomEventServiceTest {

    private GameConfigRegistry registry;
    private RandomEventService service;

    @BeforeEach
    void setUp() {
        registry = mock(GameConfigRegistry.class);

        RandomEventsConfig config = new RandomEventsConfig();

        RandomEventsConfig.RandomEventConfig event = new RandomEventsConfig.RandomEventConfig();
        event.setId("EVENT_TEST");
        event.setName("测试事件");
        event.setDescription("这是一个测试事件。");
        event.setRegionIds(List.of("MAP_AREA_MEADOW"));

        RandomEventsConfig.EventOptionConfig healOption = new RandomEventsConfig.EventOptionConfig();
        healOption.setId("HEAL");
        healOption.setText("治疗");

        RandomEventsConfig.EventOutcomeConfig goldOutcome = new RandomEventsConfig.EventOutcomeConfig();
        goldOutcome.setType("GIFT_GOLD");
        goldOutcome.setWeight(100);
        goldOutcome.setValueMin(20);
        goldOutcome.setValueMax(50);
        healOption.setOutcomes(List.of(goldOutcome));

        RandomEventsConfig.EventOptionConfig leaveOption = new RandomEventsConfig.EventOptionConfig();
        leaveOption.setId("LEAVE");
        leaveOption.setText("离开");
        RandomEventsConfig.EventOutcomeConfig nothingOutcome = new RandomEventsConfig.EventOutcomeConfig();
        nothingOutcome.setType("NOTHING");
        nothingOutcome.setWeight(100);
        leaveOption.setOutcomes(List.of(nothingOutcome));

        event.setOptions(List.of(healOption, leaveOption));
        config.setRandomEvents(List.of(event));
        when(registry.getRandomEventsConfig()).thenReturn(config);

        ItemConfig potionConfig = new ItemConfig();
        potionConfig.setId("ITEM_POTION_SMALL");
        potionConfig.setName("小型恢复药");
        when(registry.getItem("ITEM_POTION_SMALL")).thenReturn(potionConfig);

        service = new RandomEventService(registry);
    }

    @Test
    void rollRandomEvent_correctRegion_shouldReturnEvent() {
        // 多次尝试，确保触发（15% 概率）
        RandomEventService.EventView view = null;
        for (int i = 0; i < 1000; i++) {
            view = service.rollRandomEvent("MAP_AREA_MEADOW", new GameRandom(i * 7L + 13));
            if (view != null) break;
        }
        assertNotNull(view, "应在1000次尝试中至少触发一次");
        assertEquals("EVENT_TEST", view.getEventId());
        assertEquals(2, view.getOptions().size());
    }

    @Test
    void rollRandomEvent_wrongRegion_shouldReturnNull() {
        // 尝试多次都不应该触发（区域不匹配）
        for (int i = 0; i < 100; i++) {
            RandomEventService.EventView view = service.rollRandomEvent("MAP_AREA_RUINS", new GameRandom(i));
            assertNull(view);
        }
    }

    @Test
    void resolveEventOption_validOption_shouldReturnResult() {
        GameRandom random = new GameRandom(42L);
        RandomEventService.EventResultView result = service.resolveEventOption("EVENT_TEST", "HEAL", random);
        assertNotNull(result);
        assertEquals("GIFT_GOLD", result.getType());
        assertTrue(result.getGoldGained() >= 20 && result.getGoldGained() <= 50);
    }

    @Test
    void resolveEventOption_nothingOption_shouldReturnNothing() {
        GameRandom random = new GameRandom(42L);
        RandomEventService.EventResultView result = service.resolveEventOption("EVENT_TEST", "LEAVE", random);
        assertNotNull(result);
        assertEquals("NOTHING", result.getType());
    }

    @Test
    void resolveEventOption_invalidEvent_shouldThrow() {
        assertThrows(Exception.class,
                () -> service.resolveEventOption("EVENT_NOT_EXIST", "HEAL", new GameRandom()));
    }

    @Test
    void resolveEventOption_invalidOption_shouldThrow() {
        assertThrows(Exception.class,
                () -> service.resolveEventOption("EVENT_TEST", "INVALID_OPTION", new GameRandom()));
    }
}
