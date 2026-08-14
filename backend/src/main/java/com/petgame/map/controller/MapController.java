package com.petgame.map.controller;

import com.petgame.battle.service.BattleService;
import com.petgame.battle.service.BattleSnapshot;
import com.petgame.common.ApiResponse;
import com.petgame.map.service.MapExplorationService;
import com.petgame.map.service.RandomEventService;
import lombok.Data;
import org.springframework.web.bind.annotation.*;

/**
 * 地图探索与区域接口（阶段 6）。
 * <p>
 * 区域移动 / 营地恢复与传送 / 采集与宝箱 / 地图遭遇入口 / 大地图视图。
 * 遭遇的战斗流程（行动提交/查询/结算）仍复用 {@code /api/battles/**} 与
 * {@code /api/wild/**}（同一个 BattleEngine）。
 */
@RestController
@RequestMapping("/api/maps")
public class MapController {

    private final MapExplorationService mapService;
    private final BattleService battleService;
    private final RandomEventService randomEventService;

    public MapController(MapExplorationService mapService, BattleService battleService,
                         RandomEventService randomEventService) {
        this.mapService = mapService;
        this.battleService = battleService;
        this.randomEventService = randomEventService;
    }

    /** 大地图视图（需求 §116：区域、推荐等级、Boss 状态占位、营地、传送）。 */
    @GetMapping("/world")
    public ApiResponse<MapExplorationService.WorldMapView> getWorldMap() {
        return ApiResponse.success(mapService.getWorldMap());
    }

    /** 当前所在区域的访问状态（前端场景恢复用）。 */
    @GetMapping("/current")
    public ApiResponse<MapExplorationService.MapEnterView> getCurrentMap() {
        return ApiResponse.success(mapService.getCurrentMapView());
    }

    /** 进入区域（出口移动 / 大地图进入统一入口）。 */
    @PostMapping("/{mapId}/enter")
    public ApiResponse<MapExplorationService.MapEnterView> enterRegion(
            @PathVariable String mapId, @RequestBody(required = false) EnterRegionRequest request) {
        String exitId = request != null ? request.getExitId() : null;
        return ApiResponse.success(mapService.enterRegion(mapId, exitId));
    }

    /** 营地休息：免费恢复全队 + 激活营地 + 触发地图刷新（需求 §75）。 */
    @PostMapping("/camps/{campId}/rest")
    public ApiResponse<MapExplorationService.CampRestView> restAtCamp(@PathVariable String campId) {
        return ApiResponse.success(mapService.restAtCamp(campId));
    }

    /** 已激活营地间免费快速传送（需求 §75/§116）。 */
    @PostMapping("/camps/{campId}/teleport")
    public ApiResponse<MapExplorationService.MapEnterView> teleportToCamp(@PathVariable String campId) {
        return ApiResponse.success(mapService.teleportToCamp(campId));
    }

    /** 采集普通采集点（单次访问内一次性）。 */
    @PostMapping("/gathers/{gatherId}/gather")
    public ApiResponse<MapExplorationService.RewardResultView> gather(@PathVariable String gatherId) {
        return ApiResponse.success(mapService.gather(gatherId));
    }

    /** 开启隐藏宝箱（全局一次性）。 */
    @PostMapping("/chests/{chestId}/open")
    public ApiResponse<MapExplorationService.RewardResultView> openChest(@PathVariable String chestId) {
        return ApiResponse.success(mapService.openChest(chestId));
    }

    /**
     * 地图遭遇入口（阶段 6 正式遭遇）：校验刷新组属于当前区域后开始野生战斗。
     * 可见野怪接触前可调整首发（前端在进入本接口前完成队伍调整）。
     */
    @PostMapping("/encounters")
    public ApiResponse<BattleSnapshot> startMapEncounter(@RequestBody StartEncounterRequest request) {
        mapService.validateEncounterGroup(request.getGroupId());
        return ApiResponse.success(battleService.startWildBattle(request.getGroupId(), request.getSeed()));
    }

    // ==================== 随机事件接口（阶段 10） ====================

    /**
     * 尝试触发随机事件（探索时调用，概率返回事件或 null）。
     */
    @GetMapping("/events/roll")
    public ApiResponse<RandomEventService.EventView> rollRandomEvent(
            @RequestParam(required = false) String mapId) {
        String targetMapId = mapId;
        if (targetMapId == null || targetMapId.isBlank()) {
            // 默认使用当前区域
            var current = mapService.getCurrentMapView();
            targetMapId = current.getMapId();
        }
        return ApiResponse.success(randomEventService.rollRandomEvent(
                targetMapId, new com.petgame.common.GameRandom()));
    }

    /**
     * 解析事件选项（玩家选择后调用）。
     */
    @PostMapping("/events/resolve")
    public ApiResponse<RandomEventService.EventResultView> resolveEvent(
            @RequestBody ResolveEventRequest request) {
        return ApiResponse.success(randomEventService.resolveEventOption(
                request.getEventId(), request.getOptionId(), new com.petgame.common.GameRandom()));
    }

    @Data
    public static class EnterRegionRequest {
        /** 出发出口 ID（出口移动时传；大地图进入可为空，落到区域默认出生点）。 */
        private String exitId;
    }

    @Data
    public static class StartEncounterRequest {
        /** 刷新组 ID（必须声明在当前区域 encounterGroups 中）。 */
        private String groupId;
        /** 随机种子（可选，固定种子可复现遭遇）。 */
        private Long seed;
    }

    @Data
    public static class ResolveEventRequest {
        private String eventId;
        private String optionId;
    }
}
