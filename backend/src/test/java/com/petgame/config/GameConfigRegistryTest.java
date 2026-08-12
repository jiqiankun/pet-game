package com.petgame.config;

import com.petgame.config.loader.GameConfigValidator;
import com.petgame.config.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * GameConfigRegistry 克制倍率查询测试。
 * <p>
 * 直接构造配置数据测试克制关系逻辑，不依赖 Spring 容器。
 */
class GameConfigRegistryTest {

    private GameConfigRegistry registry;

    @BeforeEach
    void setUp() throws Exception {
        // 构造配置
        SystemRuleConfig system = new SystemRuleConfig();
        GameElementsConfig elements = new GameElementsConfig();

        // 9 种属性
        String[][] elems = {
                {"METAL", "金"}, {"WOOD", "木"}, {"WATER", "水"}, {"FIRE", "火"},
                {"EARTH", "土"}, {"WIND", "风"}, {"THUNDER", "雷"}, {"LIGHT", "光"}, {"DARK", "暗"}
        };
        List<GameElementConfig> elemList = new ArrayList<>();
        for (String[] e : elems) {
            GameElementConfig ge = new GameElementConfig();
            ge.setId(e[0]);
            ge.setName(e[1]);
            elemList.add(ge);
        }
        elements.setElements(elemList);

        // 克制关系
        String[][] advs = {
                {"METAL", "WOOD"}, {"WOOD", "EARTH"}, {"EARTH", "WATER"},
                {"WATER", "FIRE"}, {"FIRE", "METAL"},
                {"METAL", "WIND"}, {"WIND", "THUNDER"}, {"THUNDER", "WATER"},
                {"LIGHT", "DARK"}, {"DARK", "LIGHT"}
        };
        List<ElementAdvantageConfig> advList = new ArrayList<>();
        for (String[] a : advs) {
            ElementAdvantageConfig ea = new ElementAdvantageConfig();
            ea.setAttacker(a[0]);
            ea.setDefender(a[1]);
            advList.add(ea);
        }
        elements.setAdvantages(advList);

        // 校验
        new GameConfigValidator().validate(system, elements);

        // 通过反射注入（不依赖 Spring）
        registry = new GameConfigRegistry(null, null);
        setField(registry, "systemRules", system);
        setField(registry, "elementsConfig", elements);

        // 构建索引
        Map<String, GameElementConfig> elementIndex = new LinkedHashMap<>();
        for (GameElementConfig elem : elements.getElements()) {
            elementIndex.put(elem.getId(), elem);
        }
        setField(registry, "elementIndex", elementIndex);

        Set<String> advantageIndex = new HashSet<>();
        for (ElementAdvantageConfig adv : elements.getAdvantages()) {
            advantageIndex.add(adv.getAttacker() + "|" + adv.getDefender());
        }
        setField(registry, "advantageIndex", advantageIndex);
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(fieldName);
        f.setAccessible(true);
        f.set(target, value);
    }

    @Test
    void fireVsMetal_shouldReturnAdvantage() {
        // 火克制金
        assertEquals(1.50, registry.getElementAdvantageMultiplier("FIRE", "METAL"), 0.001);
    }

    @Test
    void metalVsFire_shouldReturnDisadvantage() {
        // 金被火克制
        assertEquals(0.75, registry.getElementAdvantageMultiplier("METAL", "FIRE"), 0.001);
    }

    @Test
    void fireVsWood_shouldReturnNeutral() {
        // 火 vs 木：无直接克制关系
        assertEquals(1.00, registry.getElementAdvantageMultiplier("FIRE", "WOOD"), 0.001);
    }

    @Test
    void sameElement_shouldReturnNeutral() {
        assertEquals(1.00, registry.getElementAdvantageMultiplier("FIRE", "FIRE"), 0.001);
    }

    @Test
    void lightVsDark_shouldReturnAdvantage() {
        // 光⇄暗互克
        assertEquals(1.50, registry.getElementAdvantageMultiplier("LIGHT", "DARK"), 0.001);
        assertEquals(1.50, registry.getElementAdvantageMultiplier("DARK", "LIGHT"), 0.001);
    }

    @Test
    void metalVsWind_shouldReturnAdvantage() {
        // 金克制风
        assertEquals(1.50, registry.getElementAdvantageMultiplier("METAL", "WIND"), 0.001);
    }

    @Test
    void thunderVsWater_shouldReturnAdvantage() {
        // 雷克制水
        assertEquals(1.50, registry.getElementAdvantageMultiplier("THUNDER", "WATER"), 0.001);
    }

    @Test
    void waterVsThunder_shouldReturnDisadvantage() {
        // 水被雷克制
        assertEquals(0.75, registry.getElementAdvantageMultiplier("WATER", "THUNDER"), 0.001);
    }

    @Test
    void getAllElementIds_shouldReturn9() {
        assertEquals(9, registry.getAllElementIds().size());
    }

    @Test
    void getElement_existingId_shouldReturn() {
        assertNotNull(registry.getElement("FIRE"));
        assertEquals("火", registry.getElement("FIRE").getName());
    }

    @Test
    void getElement_nonExisting_shouldReturnNull() {
        assertNull(registry.getElement("DRAGON"));
    }
}
