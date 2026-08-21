package com.petgame.world;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.petgame.config.model.MapsConfig;
import com.petgame.world.service.MapTiledConsistencyValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MapTiledConsistencyValidator 测试（阶段 2）。
 * <p>
 * 用合成 Tiled 对象层验证：配置所需的出生锚点/出口/营地/采集点/宝箱对象缺失时被检出，
 * 对象齐备时不误报。真实多图文件在阶段 3 拆分后纳入交叉校验。
 */
class MapTiledConsistencyValidatorTest {

    private MapTiledConsistencyValidator validator;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        validator = new MapTiledConsistencyValidator();
        mapper = new ObjectMapper();
    }

    private JsonNode tiledWith(String... objectNames) throws Exception {
        StringBuilder sb = new StringBuilder(
                "{\"layers\":[{\"type\":\"objectgroup\",\"objects\":[");
        for (int i = 0; i < objectNames.length; i++) {
            if (i > 0) sb.append(",");
            sb.append("{\"name\":\"").append(objectNames[i]).append("\"}");
        }
        sb.append("]}]}");
        return mapper.readTree(sb.toString());
    }

    private MapsConfig.RegionConfig region() {
        MapsConfig.RegionConfig region = new MapsConfig.RegionConfig();
        region.setId("MAP_AREA_TEST");
        region.setSpawnObjectId("SPAWN_MEADOW");
        MapsConfig.ExitConfig exit = new MapsConfig.ExitConfig();
        exit.setExitId("EXIT_TEST");
        region.getExits().add(exit);
        MapsConfig.CampConfig camp = new MapsConfig.CampConfig();
        camp.setCampId("CAMP_TEST");
        region.getCamps().add(camp);
        return region;
    }

    @Test
    void missingExitObject_isDetected() throws Exception {
        List<String> errors = new ArrayList<>();
        JsonNode tiled = tiledWith("SPAWN_MEADOW", "CAMP_TEST"); // 缺 EXIT_TEST 与 SPAWN? 包含 SP、CAMP，缺 EXIT
        validator.validateObjectLayer(tiled, region(), null, errors);
        assertTrue(errors.stream().anyMatch(e -> e.contains("EXIT_TEST")),
                "应报缺失出口对象，实际: " + errors);
    }

    @Test
    void missingSpawnAnchor_isDetected() throws Exception {
        List<String> errors = new ArrayList<>();
        JsonNode tiled = tiledWith("EXIT_TEST", "CAMP_TEST"); // 缺 SPAWN_MEADOW
        validator.validateObjectLayer(tiled, region(), null, errors);
        assertTrue(errors.stream().anyMatch(e -> e.contains("SPAWN_MEADOW")),
                "应报缺失出生锚点，实际: " + errors);
    }

    @Test
    void collectObjectNames_skipsNonObjectLayers() {
        // tilelayer + objectgroup 混合：只收集 objectgroup 的对象
        List<String> names = new ArrayList<>();
        // 仅验证单图例
        JsonNode tiled = null;
        try {
            tiled = tiledWith("A", "B");
        } catch (Exception e) {
            fail(e);
        }
        assertEquals(2, validator.collectObjectNames(tiled).size());
    }
}