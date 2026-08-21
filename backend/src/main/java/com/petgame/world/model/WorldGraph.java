package com.petgame.world.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * WorldGraph：世界的只读视图模型（阶段 2）。
 * <p>
 * 由 {@code MapExplorationService} 所依赖的 MapsConfig 构建而来，
 * 将既有「区域 = 一张兼容地图」映射为 World → Region → Map → Connection/Gateway/Anchor。
 * 配置（maps.yml）是 WorldTruth 的唯一事实源，Tiled 与前端只消费该图谱投影，
 * 不在 Vue / Phaser / YAML 三处各自硬编码连接。
 * <p>
 * 本类只承载图谱拓扑事实；玩家侧的「已发现 / 已开通」知识存 MySQL（PlayerKnowledge），
 * 查询世界时由 WorldTruthService 依据知识状态过滤后才面向前端。
 */
@Data
public class WorldGraph {

    /** 世界 ID（常量，当前仅一个世界）。 */
    private String worldId = "WORLD_1";

    /** 世界展示名。 */
    private String name = "宠物精灵世界";

    /** 世界范围内全部地图节点。 */
    private List<WorldMapNode> maps = new ArrayList<>();

    /** 世界范围内全部连接（含方向，普通双向连接成对出现）。 */
    private List<WorldConnection> connections = new ArrayList<>();

    /** 世界入口地图（新游戏出生所在地图）。 */
    private String startMapId;

    /** 地图节点（阶段 2 兼容期，一个区域 ↔ 一张地图）。 */
    @Data
    public static class WorldMapNode {
        /** 地图 ID（兼容期 == regionId）。 */
        private String mapId;
        /** 所属区域 ID。 */
        private String regionId;
        /** 展示名。 */
        private String name;
        /** 区域类型：BASE / AREA。 */
        private String type;
        /** 推荐等级描述。 */
        private String recommendedLevel;
        /** Tiled 地图资源文件名。 */
        private String mapFile;
        /** 地图角色描述（maps.yml 可定义，用于阶段 3 图信息展示）。 */
        private String mapRole;
        /** 出生锚点对象 ID（Tiled spawn 对象）。 */
        private String spawnAnchorId;
        /** 推荐进入等级（阶段 13，野外缩放基准）。 */
        private int recommendedEnemyLevel;
        /** 安全区锚点（营地 / 出生点对象 ID）。 */
        private List<String> safeZoneAnchorIds = new ArrayList<>();
        /** 允许发起野生遭遇的刷新组。 */
        private List<String> encounterGroups = new ArrayList<>();
        /** 访问等级边界：未知 / known 由知识状态决定。 */
        private boolean planned;

        /** 该节点作为边，从自身出发的连接。 */
        private List<WorldConnection> outgoing = new ArrayList<>();
    }

    /** 图上一条连接（方向化；普通双向在构建时成对生成）。 */
    @Data
    public static class WorldConnection {
        /** 连接 ID（build 时派生：{from}::{to}）。 */
        private String connectionId;
        /** 起点地图。 */
        private String fromMapId;
        /** 终点地图。 */
        private String toMapId;
        /** 起点侧出口对象 ID（对应 Tiled exit 对象 == ExitConfig.exitId）。 */
        private String fromGatewayId;
        /** 终点侧入口对象 ID（对应 ExitConfig.entryObjectId）。 */
        private String toGatewayId;
        /** 连接语义：是否隐藏路线（未知时不出现在普通图谱响应）。 */
        private boolean hidden;
        /** 连接语义：是否捷径（对应地图变化解锁，OPEN_SHORTCUT）。 */
        private boolean shortcut;
        /** 是否单向 / 危险连接（无法原路返回时显式标注）。 */
        private boolean oneWay;
        /** 连接展示名。 */
        private String name;
    }
}