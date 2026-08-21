package com.petgame.config;

import com.petgame.config.loader.GameConfigLoader;
import com.petgame.config.loader.GameConfigValidator;
import com.petgame.config.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 地图与区域配置校验测试（阶段 6，配置校验规约 §2.6）。
 * <p>
 * 覆盖：真实 maps.yml 可通过启动校验、区域 ID 重复、出口目标不存在、
 * 出口指向预留区域、奖励引用不存在道具、初始地图非法。
 */
class GameConfigMapValidateTest {

    private GameConfigLoader loader;
    private GameConfigValidator validator;

    private SystemRuleConfig system;
    private GameElementsConfig elements;
    private InitialPetsConfig initialPets;
    private SkillsConfig skills;
    private StatusesConfig statuses;
    private TestBattleConfig testBattle;
    private ItemsConfig items;
    private PetsConfig pets;
    private EncountersConfig encounters;
    private ReleaseGiftsConfig releaseGifts;
    private MapsConfig maps;

    @BeforeEach
    void setUp() {
        GameProperties props = new GameProperties();
        props.setConfigDir(null);
        loader = new GameConfigLoader(props);
        validator = new GameConfigValidator();

        system = loader.loadSystemConfig();
        elements = loader.loadElementsConfig();
        initialPets = loader.loadInitialPetsConfig();
        skills = loader.loadSkillsConfig();
        statuses = loader.loadStatusesConfig();
        testBattle = loader.loadTestBattleConfig();
        items = loader.loadItemsConfig();
        pets = loader.loadPetsConfig();
        encounters = loader.loadEncountersConfig();
        releaseGifts = loader.loadReleaseGiftsConfig();
        maps = loader.loadMapsConfig();
    }

    private void validateAll(MapsConfig mapsConfig) {
        validator.validate(system, elements, initialPets, skills, statuses, testBattle,
                items, pets, encounters, releaseGifts, mapsConfig);
    }

    @Test
    void realMapsConfig_shouldPassValidation() {
        assertDoesNotThrow(() -> validateAll(maps));
    }

    @Test
    void realMapsConfig_containsStartVillageInitialAreaForest() {
        List<String> ids = maps.getRegions().stream().map(MapsConfig.RegionConfig::getId).toList();
        assertTrue(ids.contains("MAP_START_VILLAGE"));
        assertTrue(ids.contains("MAP_AREA_MEADOW"));
        assertTrue(ids.contains("MAP_AREA_FOREST"));
        // 初始地图必须是已实装区域
        assertFalse(maps.getRegions().stream()
                .filter(r -> r.getId().equals(initialPets.getInitialMapId()))
                .findFirst().orElseThrow().isPlanned());
    }

    @Test
    void duplicateRegionId_shouldFail() {
        MapsConfig.RegionConfig dup = new MapsConfig.RegionConfig();
        dup.setId("MAP_AREA_MEADOW");
        dup.setName("重复区域");
        dup.setUnlockType("AUTO");
        dup.setMapFile("meadow");
        List<MapsConfig.RegionConfig> regions = new ArrayList<>(maps.getRegions());
        regions.add(dup);
        maps.setRegions(regions);

        assertThrows(IllegalStateException.class, () -> validateAll(maps));
    }

    @Test
    void exitTargetNotExists_shouldFail() {
        maps.getRegions().stream()
                .filter(r -> "MAP_AREA_MEADOW".equals(r.getId()))
                .findFirst().orElseThrow()
                .getExits().get(0).setTargetMapId("MAP_NOT_EXISTS");

        assertThrows(IllegalStateException.class, () -> validateAll(maps));
    }

    @Test
    void exitTargetPlannedRegion_shouldFail() {
        // 当前配置已全部实装，构造一个结构预留区域作为出口目标
        addPlannedRegion("MAP_AREA_FAKE_PLANNED");
        maps.getRegions().stream()
                .filter(r -> "MAP_AREA_MEADOW".equals(r.getId()))
                .findFirst().orElseThrow()
                .getExits().get(0).setTargetMapId("MAP_AREA_FAKE_PLANNED");

        assertThrows(IllegalStateException.class, () -> validateAll(maps));
    }

    @Test
    void gatherRewardInvalidItem_shouldFail() {
        maps.getRegions().stream()
                .filter(r -> "MAP_AREA_MEADOW".equals(r.getId()))
                .findFirst().orElseThrow()
                .getGathers().get(0).getRewards().get(0).setItemId("ITEM_NOT_EXISTS");

        assertThrows(IllegalStateException.class, () -> validateAll(maps));
    }

    @Test
    void encounterGroupNotExists_shouldFail() {
        maps.getRegions().stream()
                .filter(r -> "MAP_AREA_MEADOW".equals(r.getId()))
                .findFirst().orElseThrow()
                .setEncounterGroups(List.of("ENCOUNTER_NOT_EXISTS"));

        assertThrows(IllegalStateException.class, () -> validateAll(maps));
    }

    @Test
    void initialMapIdPlannedRegion_shouldFail() {
        // 当前配置已全部实装，构造一个结构预留区域作为初始地图
        addPlannedRegion("MAP_AREA_FAKE_PLANNED");
        initialPets.setInitialMapId("MAP_AREA_FAKE_PLANNED");

        assertThrows(IllegalStateException.class, () -> validateAll(maps));
    }

    // ==================== 阶段 2：WorldGraph 连接与锚点校验 ====================

    @Test
    void bidirectionalConnectionWithoutReturnExit_shouldFail() {
        // 移除 MEADOW→VILLAGE 的反向出口（VILLAGE 到 MEADOW），破坏双向配对
        MapsConfig.RegionConfig village = maps.getRegions().stream()
                .filter(r -> "MAP_START_VILLAGE".equals(r.getId()))
                .findFirst().orElseThrow();
        village.getExits().removeIf(e -> "MAP_AREA_MEADOW".equals(e.getTargetMapId()));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> validateAll(maps));
        assertTrue(ex.getMessage().contains("缺少反向出口"),
                "应报缺少反向出口，实际: " + ex.getMessage());
    }

    @Test
    void oneWayConnection_allowsNoReturnExit() {
        // MEADOW→VILLAGE 标注 oneWay=true 后，即使 VILLAGE 无返回出口也不报错
        MapsConfig.RegionConfig village = maps.getRegions().stream()
                .filter(r -> "MAP_START_VILLAGE".equals(r.getId()))
                .findFirst().orElseThrow();
        village.getExits().removeIf(e -> "MAP_AREA_MEADOW".equals(e.getTargetMapId()));
        MapsConfig.RegionConfig meadow = maps.getRegions().stream()
                .filter(r -> "MAP_AREA_MEADOW".equals(r.getId()))
                .findFirst().orElseThrow();
        meadow.getExits().stream()
                .filter(e -> "MAP_START_VILLAGE".equals(e.getTargetMapId()))
                .forEach(e -> e.setOneWay(true));

        assertDoesNotThrow(() -> validateAll(maps));
    }

    @Test
    void missingEntryObjectId_shouldFail() {
        maps.getRegions().stream()
                .filter(r -> "MAP_AREA_MEADOW".equals(r.getId()))
                .findFirst().orElseThrow()
                .getExits().get(0).setEntryObjectId(null);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> validateAll(maps));
        assertTrue(ex.getMessage().contains("缺少 entryObjectId"),
                "应报缺少到达锚点，实际: " + ex.getMessage());
    }

    @Test
    void missingSpawnObjectId_shouldFail() {
        maps.getRegions().stream()
                .filter(r -> "MAP_AREA_MEADOW".equals(r.getId()))
                .findFirst().orElseThrow()
                .setSpawnObjectId("   ");

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> validateAll(maps));
        assertTrue(ex.getMessage().contains("出生锚点"),
                "应报缺少出生锚点，实际: " + ex.getMessage());
    }

    /** 构造一个结构预留区域（planned=true）用于负面用例。 */
    private void addPlannedRegion(String regionId) {
        MapsConfig.RegionConfig planned = new MapsConfig.RegionConfig();
        planned.setId(regionId);
        planned.setName("测试预留区域");
        planned.setPlanned(true);
        planned.setUnlockType("BOSS");
        List<MapsConfig.RegionConfig> regions = new ArrayList<>(maps.getRegions());
        regions.add(planned);
        maps.setRegions(regions);
    }
}
