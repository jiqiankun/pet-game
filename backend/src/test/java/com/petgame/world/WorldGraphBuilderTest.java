package com.petgame.world;

import com.petgame.config.GameProperties;
import com.petgame.config.loader.GameConfigLoader;
import com.petgame.config.model.MapsConfig;
import com.petgame.world.model.WorldGraph;
import com.petgame.world.service.WorldGraphBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * WorldGraphBuilder 测试（阶段 2）。
 * <p>
 * 验证从真实 maps.yml 派生的 World → Region → Map 层级符合语义：
 * 6 个实装地图节点、起点地图、出生锚点、安全区承接、方向化连接与对称出口成对出现。
 */
class WorldGraphBuilderTest {

    private WorldGraphBuilder builder;
    private MapsConfig maps;

    @BeforeEach
    void setUp() {
        GameProperties props = new GameProperties();
        props.setConfigDir(null);
        maps = new GameConfigLoader(props).loadMapsConfig();
        builder = new WorldGraphBuilder();
    }

    @Test
    void build_containsAllImplementedMaps() {
        WorldGraph graph = builder.build(maps);
        long planned = maps.getRegions().stream().filter(MapsConfig.RegionConfig::isPlanned).count();
        // 图谱只含实装区域
        assertEquals(maps.getRegions().size() - planned, graph.getMaps().size());
        Map<String, WorldGraph.WorldMapNode> byId = graph.getMaps().stream()
                .collect(Collectors.toMap(WorldGraph.WorldMapNode::getMapId, Function.identity()));
        assertTrue(byId.containsKey("MAP_START_VILLAGE"));
        assertTrue(byId.containsKey("MAP_AREA_MEADOW"));
        assertTrue(byId.containsKey("MAP_AREA_FOREST"));
        assertTrue(byId.containsKey("MAP_AREA_RUINS"));
    }

    @Test
    void build_startMap_isAutoUnlockedImplementedRegion() {
        WorldGraph graph = builder.build(maps);
        assertEquals("MAP_START_VILLAGE", graph.getStartMapId());
    }

    @Test
    void build_mapsHaveSpawnAnchorAndSafeZone() {
        WorldGraph graph = builder.build(maps);
        WorldGraph.WorldMapNode meadow = graph.getMaps().stream()
                .filter(n -> n.getMapId().equals("MAP_AREA_MEADOW")).findFirst().orElseThrow();
        assertEquals("SPAWN_MEADOW", meadow.getSpawnAnchorId());
        // 安全区包含营地对象与出生点
        assertTrue(meadow.getSafeZoneAnchorIds().contains("CAMP_MEADOW_1"));
        assertTrue(meadow.getSafeZoneAnchorIds().contains("SPAWN_MEADOW"));
    }

    @Test
    void build_connectionGatewayAndReturnPair() {
        WorldGraph graph = builder.build(maps);
        // meadow → village 与 village → meadow 应各自成对，且带各自 Gateway
        WorldGraph.WorldConnection out = graph.getConnections().stream()
                .filter(c -> c.getFromMapId().equals("MAP_AREA_MEADOW"))
                .filter(c -> c.getToMapId().equals("MAP_START_VILLAGE"))
                .findFirst().orElseThrow();
        assertEquals("EXIT_MEADOW_TO_VILLAGE", out.getFromGatewayId());
        assertEquals("ENTRY_VILLAGE_FROM_MEADOW", out.getToGatewayId());
        WorldGraph.WorldConnection back = graph.getConnections().stream()
                .filter(c -> c.getFromMapId().equals("MAP_START_VILLAGE"))
                .filter(c -> c.getToMapId().equals("MAP_AREA_MEADOW"))
                .findFirst().orElseThrow();
        assertEquals("EXIT_VILLAGE_TO_MEADOW", back.getFromGatewayId());
        assertEquals("ENTRY_MEADOW_FROM_VILLAGE", back.getToGatewayId());
    }

    @Test
    void build_defaultHiddenAndShortcutFalse() {
        WorldGraph graph = builder.build(maps);
        // 无显式标注的连接：隐藏/捷径默认推导为 false（不引入语义漂移）
        // 唯一的方向性由进入无出口尾区（遗迹）的 oneWay 显式标注承载
        assertTrue(graph.getConnections().stream()
                .filter(c -> !c.isOneWay())
                .noneMatch(c -> c.isHidden() || c.isShortcut()));
    }

    @Test
    void build_oneWayConnections_toRuinsSink() {
        WorldGraph graph = builder.build(maps);
        // 目的地遗迹无返回出口，WATERS/THUNDER → RUINS 须显式标注 oneWay
        Map<String, WorldGraph.WorldConnection> byId = graph.getConnections().stream()
                .collect(Collectors.toMap(WorldGraph.WorldConnection::getFromGatewayId, Function.identity()));
        assertTrue(byId.get("EXIT_WATERS_TO_RUINS").isOneWay());
        assertTrue(byId.get("EXIT_THUNDER_TO_RUINS").isOneWay());
        // 其余往返连接（如 MEADOW↔VILLAGE）应为双向
        assertFalse(byId.get("EXIT_MEADOW_TO_VILLAGE").isOneWay());
        assertFalse(byId.get("EXIT_VILLAGE_TO_MEADOW").isOneWay());
    }
}