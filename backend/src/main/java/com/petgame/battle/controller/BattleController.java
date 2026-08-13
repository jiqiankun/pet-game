package com.petgame.battle.controller;

import com.petgame.battle.model.BattleAction;
import com.petgame.battle.service.BattleService;
import com.petgame.battle.service.BattleSnapshot;
import com.petgame.common.ApiResponse;
import lombok.Data;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 战斗接口（阶段 3）。
 * <p>
 * 前端只提交行动意图，不提交计算结果；伤害、命中、暴击、胜负一律由后端计算。
 * 战斗临时数据只存服务器内存，战斗过程零数据库写入。
 */
@RestController
@RequestMapping("/api/battles")
public class BattleController {

    private final BattleService battleService;

    public BattleController(BattleService battleService) {
        this.battleService = battleService;
    }

    /**
     * 开始战斗。阶段 3 仅支持 type = TEST_BATTLE（固定敌方阵容测试战斗）。
     * seed 可选：开发者模式固定种子复现战斗。
     */
    @PostMapping
    public ApiResponse<BattleSnapshot> startBattle(@RequestBody StartBattleRequest request) {
        String type = request.getType() != null ? request.getType().toUpperCase() : "TEST_BATTLE";
        if (!"TEST_BATTLE".equals(type)) {
            return ApiResponse.error("UNSUPPORTED_BATTLE_TYPE", "阶段 3 仅支持测试战斗: " + request.getType());
        }
        return ApiResponse.success(battleService.startTestBattle(request.getSeed()));
    }

    /**
     * 查询战斗当前状态。
     */
    @GetMapping("/{battleId}")
    public ApiResponse<BattleSnapshot> getBattle(@PathVariable String battleId) {
        return ApiResponse.success(battleService.getBattle(battleId));
    }

    /**
     * 提交玩家行动意图并结算一整个回合（技术方案 §43）。
     */
    @PostMapping("/{battleId}/actions")
    public ApiResponse<BattleSnapshot> submitActions(@PathVariable String battleId,
                                                     @RequestBody SubmitActionsRequest request) {
        return ApiResponse.success(battleService.submitActions(battleId, request.getActions()));
    }

    @Data
    public static class StartBattleRequest {
        private String type;
        private Long seed;
    }

    @Data
    public static class SubmitActionsRequest {
        private List<BattleAction> actions;
    }
}
