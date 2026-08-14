package com.petgame.config;

import com.petgame.config.loader.GameConfigValidator;
import com.petgame.config.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 阶段 10 配置校验测试。
 * <p>
 * 验证：商店/随机事件/推荐Build 配置校验。
 * 通过反射调用私有校验方法，避免全量校验对测试数据完整性的要求。
 */
class Phase10ConfigValidateTest {

    private GameConfigValidator validator;

    @BeforeEach
    void setUp() {
        validator = new GameConfigValidator();
    }

    // ==================== 商店校验 ====================

    @Test
    void validateShop_validConfig_shouldPass() throws Exception {
        ItemsConfig items = new ItemsConfig();
        ItemConfig item = new ItemConfig();
        item.setId("ITEM_TEST");
        items.setItems(List.of(item));

        ShopConfig shop = new ShopConfig();
        ShopConfig.ShopItemConfig shopItem = new ShopConfig.ShopItemConfig();
        shopItem.setItemId("ITEM_TEST");
        shopItem.setPrice(100);
        shop.setShopItems(List.of(shopItem));

        List<String> errors = new ArrayList<>();
        invokePrivate("validateShop", ShopConfig.class, ItemsConfig.class, QuestsConfig.class, List.class)
                .invoke(validator, shop, items, null, errors);
        assertTrue(errors.isEmpty(), "应无错误: " + errors);
    }

    @Test
    void validateShop_invalidItemRef_shouldFail() throws Exception {
        ItemsConfig items = new ItemsConfig();
        items.setItems(List.of());

        ShopConfig shop = new ShopConfig();
        ShopConfig.ShopItemConfig shopItem = new ShopConfig.ShopItemConfig();
        shopItem.setItemId("ITEM_NOT_EXIST");
        shop.setShopItems(List.of(shopItem));

        List<String> errors = new ArrayList<>();
        invokePrivate("validateShop", ShopConfig.class, ItemsConfig.class, QuestsConfig.class, List.class)
                .invoke(validator, shop, items, null, errors);
        assertFalse(errors.isEmpty(), "应报告道具引用错误");
    }

    @Test
    void validateShop_negativePrice_shouldFail() throws Exception {
        ItemsConfig items = new ItemsConfig();
        ItemConfig item = new ItemConfig();
        item.setId("ITEM_TEST");
        items.setItems(List.of(item));

        ShopConfig shop = new ShopConfig();
        ShopConfig.ShopItemConfig shopItem = new ShopConfig.ShopItemConfig();
        shopItem.setItemId("ITEM_TEST");
        shopItem.setPrice(-10);
        shop.setShopItems(List.of(shopItem));

        List<String> errors = new ArrayList<>();
        invokePrivate("validateShop", ShopConfig.class, ItemsConfig.class, QuestsConfig.class, List.class)
                .invoke(validator, shop, items, null, errors);
        assertFalse(errors.isEmpty(), "应报告价格错误");
    }

    // ==================== 随机事件校验 ====================

    @Test
    void validateRandomEvents_validConfig_shouldPass() throws Exception {
        MapsConfig maps = new MapsConfig();
        MapsConfig.RegionConfig region = new MapsConfig.RegionConfig();
        region.setId("MAP_TEST");
        maps.setRegions(List.of(region));

        EncountersConfig encounters = new EncountersConfig();
        EncountersConfig.EncounterGroup group = new EncountersConfig.EncounterGroup();
        group.setId("ENCOUNTER_TEST");
        encounters.setEncounterGroups(List.of(group));

        ItemsConfig items = new ItemsConfig();
        ItemConfig item = new ItemConfig();
        item.setId("ITEM_TEST");
        items.setItems(List.of(item));

        RandomEventsConfig events = new RandomEventsConfig();
        RandomEventsConfig.RandomEventConfig event = new RandomEventsConfig.RandomEventConfig();
        event.setId("EVENT_TEST");
        event.setRegionIds(List.of("MAP_TEST"));
        RandomEventsConfig.EventOptionConfig option = new RandomEventsConfig.EventOptionConfig();
        option.setId("OPT_1");
        RandomEventsConfig.EventOutcomeConfig outcome = new RandomEventsConfig.EventOutcomeConfig();
        outcome.setType("GIFT_ITEM");
        outcome.setWeight(100);
        outcome.setItemPool(List.of("ITEM_TEST"));
        option.setOutcomes(List.of(outcome));
        event.setOptions(List.of(option));
        events.setRandomEvents(List.of(event));

        List<String> errors = new ArrayList<>();
        invokePrivate("validateRandomEvents", RandomEventsConfig.class, ItemsConfig.class,
                MapsConfig.class, EncountersConfig.class, List.class)
                .invoke(validator, events, items, maps, encounters, errors);
        assertTrue(errors.isEmpty(), "应无错误: " + errors);
    }

    @Test
    void validateRandomEvents_duplicateId_shouldFail() throws Exception {
        RandomEventsConfig events = new RandomEventsConfig();
        RandomEventsConfig.RandomEventConfig event1 = new RandomEventsConfig.RandomEventConfig();
        event1.setId("EVENT_DUP");
        RandomEventsConfig.RandomEventConfig event2 = new RandomEventsConfig.RandomEventConfig();
        event2.setId("EVENT_DUP");
        events.setRandomEvents(List.of(event1, event2));

        List<String> errors = new ArrayList<>();
        invokePrivate("validateRandomEvents", RandomEventsConfig.class, ItemsConfig.class,
                MapsConfig.class, EncountersConfig.class, List.class)
                .invoke(validator, events, null, null, null, errors);
        assertFalse(errors.isEmpty(), "应报告重复 ID 错误");
    }

    // ==================== 推荐Build校验 ====================

    @Test
    void validateBuildRecommendations_validConfig_shouldPass() throws Exception {
        PetsConfig pets = new PetsConfig();
        PetSpeciesConfig species = new PetSpeciesConfig();
        species.setId("PET_TEST");
        pets.setSpecies(List.of(species));

        SkillsConfig skills = new SkillsConfig();
        SkillConfig skill = new SkillConfig();
        skill.setId("SKILL_TEST");
        skills.setSkills(List.of(skill));

        BuildRecommendationConfig builds = new BuildRecommendationConfig();
        BuildRecommendationConfig.SpeciesBuildConfig rec = new BuildRecommendationConfig.SpeciesBuildConfig();
        rec.setSpeciesId("PET_TEST");
        BuildRecommendationConfig.BuildConfig build = new BuildRecommendationConfig.BuildConfig();
        build.setName("Test Build");
        build.setStatPriority(List.of("STRENGTH", "SPEED"));
        build.setSkillPriority(List.of("SKILL_TEST"));
        rec.setBuilds(List.of(build));
        builds.setRecommendations(List.of(rec));

        List<String> errors = new ArrayList<>();
        invokePrivate("validateBuildRecommendations", BuildRecommendationConfig.class,
                PetsConfig.class, SkillsConfig.class, List.class)
                .invoke(validator, builds, pets, skills, errors);
        assertTrue(errors.isEmpty(), "应无错误: " + errors);
    }

    @Test
    void validateBuildRecommendations_invalidSpecies_shouldFail() throws Exception {
        PetsConfig pets = new PetsConfig();
        pets.setSpecies(List.of());

        BuildRecommendationConfig builds = new BuildRecommendationConfig();
        BuildRecommendationConfig.SpeciesBuildConfig rec = new BuildRecommendationConfig.SpeciesBuildConfig();
        rec.setSpeciesId("PET_NOT_EXIST");
        builds.setRecommendations(List.of(rec));

        List<String> errors = new ArrayList<>();
        invokePrivate("validateBuildRecommendations", BuildRecommendationConfig.class,
                PetsConfig.class, SkillsConfig.class, List.class)
                .invoke(validator, builds, pets, null, errors);
        assertFalse(errors.isEmpty(), "应报告种族引用错误");
    }

    // ==================== 反射工具 ====================

    private Method invokePrivate(String name, Class<?>... paramTypes) throws Exception {
        Method m = GameConfigValidator.class.getDeclaredMethod(name, paramTypes);
        m.setAccessible(true);
        return m;
    }
}
