package com.petgame.world.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.petgame.common.BusinessException;
import com.petgame.config.GameConfigRegistry;
import com.petgame.config.model.MapsConfig;
import com.petgame.map.entity.PlayerCampActivationEntity;
import com.petgame.map.mapper.PlayerCampActivationMapper;
import com.petgame.map.mapper.PlayerRegionUnlockMapper;
import com.petgame.player.entity.PlayerEntity;
import com.petgame.player.mapper.PlayerMapper;
import com.petgame.world.entity.PlayerKnownLocationEntity;
import com.petgame.world.entity.PlayerWorldStateEntity;
import com.petgame.world.mapper.PlayerKnownLocationMapper;
import com.petgame.world.mapper.PlayerWorldStateMapper;
import com.petgame.world.model.WorldGraph;
import com.petgame.world.model.LocationRef;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * WorldTruthService（阶段 2）：世界事实（配置图谱）与玩家知识 / 世界状态的读写服务。
 * <p>
 * 职责：
 * <ul>
 *   <li>{@link #getOrInit(String)}：按旧存档兼容规则初始化 world_state 与知识，旧档案可读。</li>
 *   <li>{@link #getWorldView()}：依据 PlayerKnowledge 过滤图谱后返回面向前端的 World/Region/Map 层级。</li>
 *   <li>{@link #updatePosition}：校验并保存当前地图 / 坐标 / 朝向，拒绝非法跨图位置。</li>
 *   <li>{@link #discoverLocation}：受控写入"已发现/已解锁"知识，拒绝伪造节点。</li>
 * </ul>
 * 服务端是全量图谱知识过滤的唯一裁决者，隐藏连接未发现前不会下发。
 */
@Service
public class WorldTruthService {

    private static final Logger log = LoggerFactory.getLogger(WorldTruthService.class);

    /** 知识类型常量。 */
    public static final String T_REGION = "REGION";
    public static final String T_MAP = "MAP";
    public static final String T_CONNECTION = "CONNECTION";
    public static final String T_LANDMARK = "LANDMARK";
    public static final String T_SHORTCUT = "SHORTCUT";

    private final GameConfigRegistry registry;
    private final WorldGraphBuilder worldGraphBuilder;
    private final PlayerMapper playerMapper;
    private final PlayerWorldStateMapper worldStateMapper;
    private final PlayerKnownLocationMapper knownLocationMapper;
    private final PlayerRegionUnlockMapper regionUnlockMapper;
    private final PlayerCampActivationMapper campActivationMapper;

    public WorldTruthService(GameConfigRegistry registry,
                             WorldGraphBuilder worldGraphBuilder,
                             PlayerMapper playerMapper,
                             PlayerWorldStateMapper worldStateMapper,
                             PlayerKnownLocationMapper knownLocationMapper,
                             @Lazy PlayerRegionUnlockMapper regionUnlockMapper,
                             @Lazy PlayerCampActivationMapper campActivationMapper) {
        this.registry = registry;
        this.worldGraphBuilder = worldGraphBuilder;
        this.playerMapper = playerMapper;
        this.worldStateMapper = worldStateMapper;
        this.knownLocationMapper = knownLocationMapper;
        this.regionUnlockMapper = regionUnlockMapper;
        this.campActivationMapper = campActivationMapper;
    }

    // ==================== 初始化与旧存档迁移 ====================

    /**
     * 获取或初始化存档的世界状态与知识。
     * <p>
     * 旧存档（无 world_state 行）：以 {@code player.current_map_id} 推导兼容位置，
     * 坐标无效落到配置出生锚点；知识按当前地图与可见连接初始化。
     */
    @Transactional
    public PlayerWorldStateEntity getOrInit(String saveId) {
        PlayerWorldStateEntity state = worldStateMapper.selectById(saveId);
        if (state != null) {
            return state;
        }
        PlayerEntity player = requirePlayer();
        WorldGraph graph = graph();
        String mapId = player.getCurrentMapId();
        if (mapId == null || mapId.isBlank()) {
            mapId = graph.getStartMapId();
        }
        MapsConfig.RegionConfig region = registry.getRegion(mapId);
        if (region == null || region.isPlanned()) {
            mapId = graph.getStartMapId();
            region = registry.getRegion(mapId);
        }

        state = new PlayerWorldStateEntity();
        state.setSaveId(saveId);
        state.setCurrentMapId(mapId);
        state.setCurrentRegionId(region != null ? region.getId() : mapId);
        state.setPosX(null);
        state.setPosY(null);
        state.setFacing("DOWN");
        state.setNearestSafePoint(region != null ? region.getSpawnObjectId() : null);
        state.setWorldVersion(graphVersion());
        state.setUpdatedAt(LocalDateTime.now());
        worldStateMapper.insert(state);

        initKnowledge(saveId, mapId);
        log.info("初始化世界状态：saveId={}, mapId={}（旧存档兼容迁移）", saveId, mapId);
        return state;
    }

    /** 初始化知识：当前地图 + 以当前地图为起点的可见连接；隐藏/捷径连接不预置为已发现。 */
    private void initKnowledge(String saveId, String mapId) {
        addKnown(saveId, T_MAP, mapId);
        addKnown(saveId, T_REGION, mapId);
        for (WorldGraph.WorldConnection conn : graph().getConnections()) {
            if (conn.getFromMapId().equals(mapId) && !conn.isHidden()) {
                addKnown(saveId, T_CONNECTION, conn.getConnectionId());
            }
        }
    }

    private void addKnown(String saveId, String type, String id) {
        if (id == null || id.isBlank()) {
            return;
        }
        Long existed = knownLocationMapper.selectCount(new LambdaQueryWrapper<PlayerKnownLocationEntity>()
                .eq(PlayerKnownLocationEntity::getSaveId, saveId)
                .eq(PlayerKnownLocationEntity::getLocationType, type)
                .eq(PlayerKnownLocationEntity::getLocationId, id));
        if (existed == null || existed == 0) {
            knownLocationMapper.insert(new PlayerKnownLocationEntity(
                    saveId, type, id, LocalDateTime.now()));
        }
    }

    // ==================== 世界视图（知识过滤） ====================

    /**
     * 面向前端的 World → Region → Map 层级，按 PlayerKnowledge 过滤。
     * 隐藏连接未发现前不出现在响应中；发现后重新请求才出现。普通客户端不会拿到全量隐藏数据。
     */
    public WorldView getWorldView() {
        PlayerEntity player = requirePlayer();
        String saveId = player.getSaveId();
        getOrInit(saveId);
        WorldGraph graph = graph();
        Set<String> known = loadKnownIds(saveId);

        WorldView view = new WorldView();
        view.setWorldId(graph.getWorldId());
        view.setName(graph.getName());
        view.setCurrentMapId(player.getCurrentMapId());
        view.setWorldVersion(graphVersion());

        for (WorldGraph.WorldMapNode node : graph.getMaps()) {
            if (node.isPlanned()) {
                continue;
            }
            WorldView.MapNodeView nv = new WorldView.MapNodeView();
            nv.setMapId(node.getMapId());
            nv.setRegionId(node.getRegionId());
            nv.setName(node.getName());
            nv.setType(node.getType());
            nv.setRecommendedLevel(node.getRecommendedLevel());
            nv.setMapRole(node.getMapRole());
            nv.setSpawnAnchorId(node.getSpawnAnchorId());
            nv.setSafeZoneAnchorIds(new ArrayList<>(node.getSafeZoneAnchorIds()));
            nv.setCurrent(node.getMapId().equals(player.getCurrentMapId()));
            // 地图"已发现"知识
            nv.setDiscovered(known.contains(t(T_MAP, node.getMapId())));

            for (WorldGraph.WorldConnection conn : node.getOutgoing()) {
                // 隐藏连接：仅在已发现时才下发；捷径：仅在已解锁时才下发
                if (conn.isHidden() && !known.contains(t(T_CONNECTION, conn.getConnectionId()))) {
                    continue;
                }
                if (conn.isShortcut() && !known.contains(t(T_SHORTCUT, conn.getConnectionId()))) {
                    continue;
                }
                WorldView.ConnectionView cv = new WorldView.ConnectionView();
                cv.setConnectionId(conn.getConnectionId());
                cv.setFromMapId(conn.getFromMapId());
                cv.setToMapId(conn.getToMapId());
                cv.setFromGatewayId(conn.getFromGatewayId());
                cv.setToGatewayId(conn.getToGatewayId());
                cv.setHidden(conn.isHidden());
                cv.setShortcut(conn.isShortcut());
                cv.setOneWay(conn.isOneWay());
                cv.setName(conn.getName());
                nv.getOutgoing().add(cv);
            }
            view.getMaps().add(nv);
        }
        return view;
    }

    /** 已发现/已解锁的连接与捷径 ID（面向前端透明）。 */
    public Set<String> getUnlockedShortcutIds() {
        PlayerEntity player = requirePlayer();
        getOrInit(player.getSaveId());
        Set<String> result = new HashSet<>();
        for (PlayerKnownLocationEntity e : knownLocationMapper.selectList(
                new LambdaQueryWrapper<PlayerKnownLocationEntity>()
                        .eq(PlayerKnownLocationEntity::getSaveId, player.getSaveId())
                        .eq(PlayerKnownLocationEntity::getLocationType, T_SHORTCUT))) {
            result.add(e.getLocationId());
        }
        return result;
    }

    // ==================== 位置与知识写入 ====================

    /**
     * 保存当前地图 / 坐标 / 朝向（客户端节流后提交）。
     * <p>
     * 拒绝非法跨图位置：mapId 必须对应已实装图谱节点，且只接受当前地图（避免客户端任意跨图）。
     */
    @Transactional
    public CurrentLocationView updatePosition(String mapId, Double posX, Double posY, String facing) {
        PlayerEntity player = requirePlayer();
        String saveId = player.getSaveId();
        PlayerWorldStateEntity state = getOrInit(saveId);

        WorldGraph.WorldMapNode node = findNode(mapId);
        if (node == null || node.isPlanned()) {
            throw new BusinessException("ILLEGAL_MAP", "非法地图位置: " + mapId);
        }
        // 跨图位置：若玩家当前地图与目标不同且从未进入过该地图，则拒绝（阶段 5 后由切图流程处理）
        if (!mapId.equals(player.getCurrentMapId())) {
            throw new BusinessException("POSITION_CROSS_MAP", "非法跨图位置：" + mapId
                    + "，当前地图为 " + player.getCurrentMapId());
        }

        state.setCurrentMapId(mapId);
        state.setCurrentRegionId(node.getRegionId());
        state.setPosX(posX);
        state.setPosY(posY);
        if (facing != null && !facing.isBlank()) {
            state.setFacing(facing);
        }
        state.setNearestSafePoint(resolveNearestSafePoint(saveId, node));
        state.setWorldVersion(graphVersion());
        state.setUpdatedAt(LocalDateTime.now());
        worldStateMapper.updateById(state);
        return toCurrentLocation(state, node);
    }

    /** 受控写入知识。校验节点在图谱中存在，拒绝伪造 ID。 */
    @Transactional
    public void discoverLocation(String type, String id) {
        if (!isValidKnownType(type)) {
            throw new BusinessException("KNOWLEDGE_ILLEGAL_TYPE", "非法知识类型: " + type);
        }
        // 校验节点存在
        if (T_CONNECTION.equals(type)) {
            boolean found = graph().getConnections().stream()
                    .anyMatch(c -> c.getConnectionId().equals(id));
            if (!found) {
                throw new BusinessException("KNOWLEDGE_NODE_MISSING", "连接不存在: " + id);
            }
        } else if (T_SHORTCUT.equals(type)) {
            boolean found = graph().getConnections().stream()
                    .anyMatch(c -> c.isShortcut() && c.getConnectionId().equals(id));
            if (!found) {
                throw new BusinessException("KNOWLEDGE_NODE_MISSING", "快捷连接不存在或非捷径: " + id);
            }
        } else if (T_MAP.equals(type) || T_REGION.equals(type)) {
            if (findNode(id) == null) {
                throw new BusinessException("KNOWLEDGE_NODE_MISSING", "地图不存在: " + id);
            }
        } else {
            throw new BusinessException("KNOWLEDGE_NODE_UNSUPPORTED", "暂不支持的知识节点类型: " + type);
        }
        PlayerEntity player = requirePlayer();
        addKnown(player.getSaveId(), type, id);
    }

    /** 当前精确位置（前端恢复场景用；坐标空时下发出生锚点回退）。 */
    public CurrentLocationView getCurrentLocation() {
        PlayerEntity player = requirePlayer();
        PlayerWorldStateEntity state = getOrInit(player.getSaveId());
        WorldGraph.WorldMapNode node = findNode(state.getCurrentMapId());
        if (node == null) {
            throw new BusinessException("ILLEGAL_MAP", "当前地图不存在: " + state.getCurrentMapId());
        }
        return toCurrentLocation(state, node);
    }

    // ==================== 内部工具 ====================

    private CurrentLocationView toCurrentLocation(PlayerWorldStateEntity state, WorldGraph.WorldMapNode node) {
        CurrentLocationView v = new CurrentLocationView();
        v.setMapId(state.getCurrentMapId());
        v.setRegionId(state.getCurrentRegionId());
        v.setPosX(state.getPosX());
        v.setPosY(state.getPosY());
        v.setFacing(state.getFacing());
        v.setSafeAnchorId(state.getNearestSafePoint() != null
                ? state.getNearestSafePoint() : node.getSpawnAnchorId());
        v.setSpawnAnchorId(node.getSpawnAnchorId());
        v.setWorldVersion(state.getWorldVersion());
        return v;
    }

    /** 最近有效安全点：优先当前区域已激活营地对象，其次出生锚点。 */
    private String resolveNearestSafePoint(String saveId, WorldGraph.WorldMapNode node) {
        if (campActivationMapper != null) {
            List<PlayerCampActivationEntity> camps = campActivationMapper.selectList(
                    new LambdaQueryWrapper<PlayerCampActivationEntity>()
                            .eq(PlayerCampActivationEntity::getSaveId, saveId));
            Set<String> activated = new HashSet<>();
            for (PlayerCampActivationEntity c : camps) {
                activated.add(c.getCampId());
            }
            for (String anchor : node.getSafeZoneAnchorIds()) {
                if (anchor != null && !anchor.isBlank() && activated.contains(anchor)) {
                    return anchor;
                }
            }
        }
        return node.getSpawnAnchorId();
    }

    private Set<String> loadKnownIds(String saveId) {
        Set<String> result = new HashSet<>();
        for (PlayerKnownLocationEntity e : knownLocationMapper.selectList(
                new LambdaQueryWrapper<PlayerKnownLocationEntity>()
                        .eq(PlayerKnownLocationEntity::getSaveId, saveId))) {
            result.add(t(e.getLocationType(), e.getLocationId()));
        }
        return result;
    }

    private String t(String type, String id) {
        return type + ":" + id;
    }

    private boolean isValidKnownType(String type) {
        return T_REGION.equals(type) || T_MAP.equals(type)
                || T_CONNECTION.equals(type) || T_LANDMARK.equals(type) || T_SHORTCUT.equals(type);
    }

    private WorldGraph.WorldMapNode findNode(String mapId) {
        if (mapId == null) {
            return null;
        }
        for (WorldGraph.WorldMapNode node : graph().getMaps()) {
            if (node.getMapId().equals(mapId)) {
                return node;
            }
        }
        return null;
    }

    private WorldGraph graph() {
        return worldGraphBuilder.build(registry.getMapsConfig());
    }

    private int graphVersion() {
        return registry.getMapsConfig().getConfigVersion();
    }

    private PlayerEntity requirePlayer() {
        PlayerEntity player = playerMapper.selectOne(null);
        if (player == null) {
            throw new BusinessException("NO_SAVE", "不存在存档，请先创建新游戏");
        }
        return player;
    }

    // ==================== DTO ====================

    /** World → Region → Map 层级视图（按知识过滤）。 */
    @Data
    public static class WorldView {
        private String worldId;
        private String name;
        /** 当前地图 ID。 */
        private String currentMapId;
        /** 世界状态版本（图谱变更时递增，前端据此判断是否刷新投影）。 */
        private int worldVersion;
        private List<MapNodeView> maps = new ArrayList<>();

        @Data
        public static class MapNodeView {
            private String mapId;
            private String regionId;
            private String name;
            private String type;
            private String recommendedLevel;
            private String mapRole;
            private String spawnAnchorId;
            private List<String> safeZoneAnchorIds = new ArrayList<>();
            private boolean current;
            private boolean discovered;
            private List<ConnectionView> outgoing = new ArrayList<>();
        }

        @Data
        public static class ConnectionView {
            private String connectionId;
            private String fromMapId;
            private String toMapId;
            private String fromGatewayId;
            private String toGatewayId;
            private String name;
            private boolean hidden;
            private boolean shortcut;
            private boolean oneWay;
        }
    }

    /** 当前精确位置视图。 */
    @Data
    public static class CurrentLocationView {
        private String mapId;
        private String regionId;
        private Double posX;
        private Double posY;
        private String facing;
        /** 相机/安全回退锚点对象 ID。 */
        private String safeAnchorId;
        /** 地图出生锚点对象 ID。 */
        private String spawnAnchorId;
        private Integer worldVersion;
    }
}