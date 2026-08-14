package com.petgame.boss.controller;

import com.petgame.boss.service.BossService;
import com.petgame.boss.service.BossService.*;
import com.petgame.boss.service.BossEncounterSnapshotService;
import com.petgame.common.ApiResponse;
import com.petgame.player.entity.PlayerEntity;
import com.petgame.player.service.GameService;
import lombok.Data;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Boss REST 接口（阶段 7）。
 */
@RestController
@RequestMapping("/api/bosses")
public class BossController {

    private final BossService bossService;
    private final GameService gameService;

    public BossController(BossService bossService, GameService gameService) {
        this.bossService = bossService;
        this.gameService = gameService;
    }

    /** Boss 列表（含进度/幸运/情报）。 */
    @GetMapping
    public ApiResponse<List<BossInfoDTO>> listBosses() {
        PlayerEntity player = gameService.getCurrentPlayer();
        return ApiResponse.success(bossService.getAllBossInfo(player.getSaveId()));
    }

    /** 单 Boss 详情。 */
    @GetMapping("/{bossId}")
    public ApiResponse<BossInfoDTO> getBoss(@PathVariable String bossId) {
        PlayerEntity player = gameService.getCurrentPlayer();
        return ApiResponse.success(bossService.getBossInfo(player.getSaveId(), bossId));
    }

    /** 已生成的遭遇快照；首次挑战前 data 为 null。 */
    @GetMapping("/{bossId}/encounter-snapshot")
    public ApiResponse<BossEncounterSnapshotService.SnapshotView> getEncounterSnapshot(
            @PathVariable String bossId, @RequestParam String difficulty) {
        PlayerEntity player = gameService.getCurrentPlayer();
        return ApiResponse.success(bossService.getEncounterSnapshot(player.getSaveId(), bossId, difficulty));
    }

    /** 仅当前全局难度与快照难度不一致时允许重置。 */
    @PostMapping("/{bossId}/encounter-snapshot/reset")
    public ApiResponse<BossEncounterSnapshotService.SnapshotView> resetEncounterSnapshot(
            @PathVariable String bossId, @RequestBody SnapshotRequest request) {
        PlayerEntity player = gameService.getCurrentPlayer();
        return ApiResponse.success(bossService.resetEncounterSnapshot(
                player.getSaveId(), bossId, request.getDifficulty()));
    }

    /** 开始 Boss 战斗。 */
    @PostMapping("/{bossId}/battle")
    public ApiResponse<Map<String, String>> startBattle(@PathVariable String bossId,
                                                         @RequestBody StartBossRequest request) {
        PlayerEntity player = gameService.getCurrentPlayer();
        String battleId = bossService.startBossBattle(player.getSaveId(), bossId,
                request.getDifficulty(), request.getSeed());
        return ApiResponse.success(Map.of("battleId", battleId));
    }

    /** 自动挑战。 */
    @PostMapping("/{bossId}/auto")
    public ApiResponse<AutoChallengeResultDTO> autoChallenge(@PathVariable String bossId,
                                                               @RequestBody AutoChallengeRequest request) {
        PlayerEntity player = gameService.getCurrentPlayer();
        return ApiResponse.success(bossService.autoChallenge(player.getSaveId(), bossId,
                request.getDifficulty(), request.getMode()));
    }

    /** 幸运兑换。 */
    @PostMapping("/{bossId}/exchange")
    public ApiResponse<Map<String, String>> exchangeLuck(@PathVariable String bossId,
                                                           @RequestBody ExchangeRequest request) {
        PlayerEntity player = gameService.getCurrentPlayer();
        bossService.exchangeLuck(player.getSaveId(), bossId, request.getDropItemId());
        return ApiResponse.success(Map.of("status", "ok"));
    }

    @Data
    public static class StartBossRequest {
        private String difficulty;
        private Long seed;
    }

    @Data
    public static class AutoChallengeRequest {
        private String difficulty;
        private String mode;
    }

    @Data
    public static class ExchangeRequest {
        private String dropItemId;
    }

    @Data
    public static class SnapshotRequest {
        private String difficulty;
    }
}
