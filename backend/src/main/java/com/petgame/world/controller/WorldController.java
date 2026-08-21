package com.petgame.world.controller;

import com.petgame.common.ApiResponse;
import com.petgame.world.service.WorldTruthService;
import lombok.Data;
import org.springframework.web.bind.annotation.*;

import java.util.Set;

/**
 * 世界图谱 / 玩家知识与世界状态接口（阶段 2）。
 * <p>
 * 面向前端返回经 PlayerKnowledge 过滤的 World/Region/Map 层级，以及当前精确位置、
 * 受控的知识发现写入和位置保存。非法位置 / 伪造知识 / 未开放捷径均被服务端拒绝。
 */
@RestController
@RequestMapping("/api/world")
public class WorldController {

    private final WorldTruthService worldTruthService;

    public WorldController(WorldTruthService worldTruthService) {
        this.worldTruthService = worldTruthService;
    }

    /** 世界图谱（按玩家知识过滤，隐藏连接未发现前不下发）。 */
    @GetMapping
    public ApiResponse<WorldTruthService.WorldView> getWorldView() {
        return ApiResponse.success(worldTruthService.getWorldView());
    }

    /** 当前精确位置（含安全/出生锚点回退）。 */
    @GetMapping("/current")
    public ApiResponse<WorldTruthService.CurrentLocationView> getCurrentLocation() {
        return ApiResponse.success(worldTruthService.getCurrentLocation());
    }

    /** 保存当前地图 / 坐标 / 朝向（节流后提交；拒绝非法跨图位置）。 */
    @PostMapping("/position")
    public ApiResponse<WorldTruthService.CurrentLocationView> updatePosition(
            @RequestBody UpdatePositionRequest request) {
        return ApiResponse.success(worldTruthService.updatePosition(
                request.getMapId(), request.getPosX(), request.getPosY(), request.getFacing()));
    }

    /** 已解锁捷径 ID（面向事件触发 / 前端状态透明）。 */
    @GetMapping("/shortcuts")
    public ApiResponse<Set<String>> getUnlockedShortcuts() {
        return ApiResponse.success(worldTruthService.getUnlockedShortcutIds());
    }

    /** 受控写入"已发现/已解锁"知识；伪造型校验非法 ID。 */
    @PostMapping("/discover")
    public ApiResponse<Void> discover(@RequestBody DiscoverRequest request) {
        worldTruthService.discoverLocation(request.getType(), request.getId());
        return ApiResponse.success(null);
    }

    @Data
    public static class UpdatePositionRequest {
        private String mapId;
        private Double posX;
        private Double posY;
        private String facing;
    }

    @Data
    public static class DiscoverRequest {
        /** REGION / MAP / CONNECTION / LANDMARK / SHORTCUT。 */
        private String type;
        private String id;
    }
}