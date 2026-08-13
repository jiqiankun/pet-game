package com.petgame.battle.controller;

import com.petgame.battle.model.BattleAction;
import com.petgame.battle.service.BattleService;
import com.petgame.battle.service.BattleSnapshot;
import com.petgame.common.ApiResponse;
import lombok.Data;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 战斗接口（阶段 3 起提供战斗流程；阶段 4 接入结算）。
 * <p>
 * 前端只提交行动意图，不提交计算结果；伤害、命中、暴击、胜负一律由后端计算。
 * 战斗临时数据只存服务器内存，战斗过程零数据库写入。
 * 阶段 4 新增结算接口：战斗结束后由前端主动调用，HP/经验/金币/掉落同事务落库。
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

    /**
     * 战斗结算（阶段 4 需求 §17/§85；阶段 5 扩展捕捉去向）。
     * <p>
     * 必须在战斗已结束（snapshot.finished=true）后调用。同事务完成 HP 回写、经验池/金币/掉落发放、
     * 参战宠物 battle_count/win_count 累加；野生战斗另含捕捉落库、捕捉球扣除与奖励。
     * joinTeam=true 且队伍未满 6 只时，被捕捉宠物直接入队（需求 §48），否则留在仓库。
     * 已结算的战斗不可重复结算。
     */
    @PostMapping("/{battleId}/settle")
    public ApiResponse<BattleService.BattleSettlement> settleBattle(
            @PathVariable String battleId,
            @RequestBody(required = false) SettleRequest request) {
        boolean joinTeam = request != null && Boolean.TRUE.equals(request.getJoinTeam());
        return ApiResponse.success(battleService.settleBattle(battleId, joinTeam));
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

    @Data
    public static class SettleRequest {
        /** 捕捉成功后是否直接加入队伍（队伍未满时生效，需求 §48）。 */
        private Boolean joinTeam;
    }
}
