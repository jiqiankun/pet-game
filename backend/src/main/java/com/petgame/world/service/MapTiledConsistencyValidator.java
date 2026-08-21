package com.petgame.world.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.petgame.config.model.MapsConfig;
import com.petgame.world.model.WorldGraph;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * MapTiledConsistencyValidator（阶段 2）：校验 Tiled 对象层与 WorldGraph 配置一致。
 * <p>
 * 直接解析 Tiled JSON 的对象层（objectgroup），把每张地图需要存在的对象（出生锚点、
 * 出口、营地、采集点、宝箱）与图上的对象名做比对，报告缺失，用于捕获
 * 「森林出口缺失、遗迹无返回、对象不在图」等配置与画面脱节问题（阶段 3 内容修复输入）。
 * <p>
 * 本类无状态、纯解析，不依赖 Spring；由启动/测试/构建管线按需调用。
 * Tiled 文件跨模块（前端 public/assets/maps），生产打包后才在 classpath 出现，
 * 因此对象层校验采用「能找到图就校验，找不到图则跳过」的宽容策略，不阻断启动；
 * 真实多图文件在阶段 3 重构后纳入回归。
 */
public class MapTiledConsistencyValidator {

    /**
     * 校验单张地图 Tiled 对象层是否覆盖配置所需的全部对象。
     *
     * @param tiledRoot Tiled JSON 根节点（已有对象层的解析结果）
     * @param region    对应区域配置
     * @param node      对应 WorldGraph 地图节点（可为 null，仅用于上下文说明）
     * @param errors    追加发现的缺失项（含具体对象 ID）
     */
    public void validateObjectLayer(JsonNode tiledRoot, MapsConfig.RegionConfig region,
                                    WorldGraph.WorldMapNode node, List<String> errors) {
        Set<String> objectNames = collectObjectNames(tiledRoot);
        String mapId = region.getId();

        requireObject(objectNames, mapId, "出生锚点 spawnObjectId", region.getSpawnObjectId(), errors);
        for (MapsConfig.ExitConfig exit : region.getExits()) {
            requireObject(objectNames, mapId, "出口 " + exit.getExitId(), exit.getExitId(), errors);
        }
        for (MapsConfig.CampConfig camp : region.getCamps()) {
            requireObject(objectNames, mapId, "营地 " + camp.getCampId(), camp.getCampId(), errors);
        }
        for (MapsConfig.GatherPointConfig gather : region.getGathers()) {
            requireObject(objectNames, mapId, "采集点 " + gather.getGatherId(), gather.getGatherId(), errors);
        }
        for (MapsConfig.ChestConfig chest : region.getChests()) {
            requireObject(objectNames, mapId, "宝箱 " + chest.getChestId(), chest.getChestId(), errors);
        }
    }

    private void requireObject(Set<String> objectNames, String mapId, String desc,
                               String objectId, List<String> errors) {
        if (objectId == null || objectId.isBlank()) {
            return;
        }
        if (!objectNames.contains(objectId)) {
            errors.add("地图 " + mapId + " 缺少 " + desc + " 的对象（Tiled 对象层无 " + objectId + "）");
        }
    }

    /** 收集 Tiled 所有 objectgroup 图层中的对象名。 */
    public Set<String> collectObjectNames(JsonNode tiledRoot) {
        Set<String> names = new HashSet<>();
        if (tiledRoot == null) {
            return names;
        }
        JsonNode layers = tiledRoot.get("layers");
        if (layers == null || !layers.isArray()) {
            return names;
        }
        for (JsonNode layer : layers) {
            String type = layer.path("type").asText();
            if (!"objectgroup".equals(type)) {
                continue;
            }
            JsonNode objects = layer.get("objects");
            if (objects == null || !objects.isArray()) {
                continue;
            }
            Iterator<JsonNode> it = objects.elements();
            while (it.hasNext()) {
                JsonNode obj = it.next();
                String name = obj.path("name").asText();
                if (name != null && !name.isBlank()) {
                    names.add(name);
                }
            }
        }
        return names;
    }
}