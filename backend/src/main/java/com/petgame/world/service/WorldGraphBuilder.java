package com.petgame.world.service;

import com.petgame.config.model.MapsConfig;
import com.petgame.world.model.WorldGraph;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * WorldGraphBuilder（阶段 2）：从内容配置（maps.yml）派生世界图谱拓扑。
 * <p>
 * maps.yml 是 WorldTruth 的唯一事实源；本构建器把既有「区域 = 一张兼容地图」映射为
 * World → Region → Map → Connection/Gateway/Anchor 的最小图谱，供 WorldTruthService
 * 依据玩家知识过滤后面向前端，阶段 3 再据此拆分成真实多图。
 * <p>
 * 本类无状态、可重复调用，结果只随配置变化，不随玩家变化。
 */
@Component
public class WorldGraphBuilder {

    /**
     * 构建完整世界图谱。每次基于当前配置重建（配置量小，成本可忽略）。
     *
     * @param maps 地图与区域配置（WorldTruth 唯一事实源）
     */
    public WorldGraph build(MapsConfig maps) {
        WorldGraph graph = new WorldGraph();

        Map<String, WorldGraph.WorldMapNode> nodeIndex = new LinkedHashMap<>();

        for (MapsConfig.RegionConfig region : maps.getRegions()) {
            WorldGraph.WorldMapNode node = new WorldGraph.WorldMapNode();
            node.setMapId(region.getId());
            node.setRegionId(region.getId());
            node.setName(region.getName());
            node.setType(region.getType());
            node.setRecommendedLevel(region.getRecommendedLevel());
            node.setMapFile(region.getMapFile());
            node.setMapRole(region.getMapRole());
            node.setSpawnAnchorId(region.getSpawnObjectId());
            node.setRecommendedEnemyLevel(region.getRecommendedEnemyLevel());
            node.setPlanned(region.isPlanned());
            node.getEncounterGroups().addAll(region.getEncounterGroups());
            // 安全区锚点：营地对象 + 出生点
            for (MapsConfig.CampConfig camp : region.getCamps()) {
                if (!node.getSafeZoneAnchorIds().contains(camp.getCampId())) {
                    node.getSafeZoneAnchorIds().add(camp.getCampId());
                }
            }
            if (region.getSpawnObjectId() != null
                    && !node.getSafeZoneAnchorIds().contains(region.getSpawnObjectId())) {
                node.getSafeZoneAnchorIds().add(region.getSpawnObjectId());
            }
            graph.getMaps().add(node);
            nodeIndex.put(node.getMapId(), node);
        }

        // 起始地图：AUTO 解锁的第一个实装区域作为世界入口
        for (MapsConfig.RegionConfig region : maps.getRegions()) {
            if ("AUTO".equals(region.getUnlockType()) && !region.isPlanned()) {
                graph.setStartMapId(region.getId());
                break;
            }
        }

        // 连接：从区域出口派生方向化连接；逆向来路由对方出口成对生成
        for (MapsConfig.RegionConfig region : maps.getRegions()) {
            if (region.isPlanned()) {
                continue;
            }
            WorldGraph.WorldMapNode node = nodeIndex.get(region.getId());
            if (node == null) {
                continue;
            }
            for (MapsConfig.ExitConfig exit : region.getExits()) {
                if (exit.getExitId() == null || exit.getExitId().isBlank()) {
                    continue;
                }
                WorldGraph.WorldConnection conn = new WorldGraph.WorldConnection();
                conn.setFromMapId(region.getId());
                conn.setToMapId(exit.getTargetMapId());
                conn.setFromGatewayId(exit.getExitId());
                conn.setToGatewayId(exit.getEntryObjectId());
                conn.setHidden(exit.isHidden());
                conn.setShortcut(exit.isShortcut());
                conn.setOneWay(exit.isOneWay());
                conn.setName(exit.getName());
                conn.setConnectionId(conn.getFromMapId() + "::" + conn.getToMapId());
                graph.getConnections().add(conn);
                node.getOutgoing().add(conn);
            }
        }

        // 记录节点引用，供查询使用
        graph.setMaps(Collections.unmodifiableList(new ArrayList<>(graph.getMaps())));
        return graph;
    }
}