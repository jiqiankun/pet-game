package com.petgame.capture.controller;

import com.petgame.battle.service.BattleService;
import com.petgame.battle.service.BattleSnapshot;
import com.petgame.capture.WildEncounterService;
import com.petgame.common.ApiResponse;
import com.petgame.config.GameProperties;
import lombok.Data;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 野生遭遇接口（阶段 5 捕捉）。
 * <p>
 * 提供野生战斗入口与捕捉率查询；行动提交/战斗查询/战斗结算复用
 * {@code /api/battles/**}（同一个 BattleEngine，前端统一处理）。
 * <p>
 * 捕捉球正式获取途径属后续阶段，本阶段仅新游戏赠送；
 * 开发者模式提供临时补充入口（每种捕捉球 +5），正式入口上线后移除。
 */
@RestController
@RequestMapping("/api/wild")
public class WildBattleController {

    private final BattleService battleService;
    private final GameProperties gameProperties;

    public WildBattleController(BattleService battleService, GameProperties gameProperties) {
        this.battleService = battleService;
        this.gameProperties = gameProperties;
    }

    /**
     * 开始野生战斗。groupId 默认 ENCOUNTER_GENERAL（阶段 5 临时通用刷新组）。
     * seed 可选：开发者模式固定种子复现遭遇。
     */
    @PostMapping("/battles")
    public ApiResponse<BattleSnapshot> startWildBattle(@RequestBody StartWildBattleRequest request) {
        String groupId = request.getGroupId() != null && !request.getGroupId().isBlank()
                ? request.getGroupId() : WildEncounterService.GENERAL_GROUP_ID;
        return ApiResponse.success(battleService.startWildBattle(groupId, request.getSeed()));
    }

    /**
     * 查询当前野生战斗内各存活野生单位的捕捉率（前端选择捕捉球时展示）。
     */
    @GetMapping("/battles/{battleId}/capture-rates")
    public ApiResponse<List<BattleService.CaptureRateView>> getCaptureRates(
            @PathVariable String battleId) {
        return ApiResponse.success(battleService.getCaptureRates(battleId));
    }

    /**
     * 开发者模式临时补充捕捉球（每种 +5）。仅开发者模式可用。
     */
    @PostMapping("/dev/refill-balls")
    public ApiResponse<Map<String, Integer>> devRefillBalls() {
        if (!gameProperties.isDeveloperMode()) {
            return ApiResponse.error("FORBIDDEN", "开发者模式未开启，禁止调用");
        }
        return ApiResponse.success(battleService.devRefillCaptureBalls());
    }

    @Data
    public static class StartWildBattleRequest {
        /** 刷新组 ID（默认 ENCOUNTER_GENERAL）。 */
        private String groupId;
        /** 随机种子（可选，固定种子可复现遭遇）。 */
        private Long seed;
    }
}
