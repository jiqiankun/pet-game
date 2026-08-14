package com.petgame.boss.controller;

import com.petgame.boss.service.BossChallengeService;
import com.petgame.common.ApiResponse;
import com.petgame.player.entity.PlayerEntity;
import com.petgame.player.service.GameService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Boss 挑战目标 REST 接口（阶段 11）。
 */
@RestController
@RequestMapping("/api/boss-challenges")
public class BossChallengeController {

    private final BossChallengeService bossChallengeService;
    private final GameService gameService;

    public BossChallengeController(BossChallengeService bossChallengeService, GameService gameService) {
        this.bossChallengeService = bossChallengeService;
        this.gameService = gameService;
    }

    /** Boss 挑战目标列表（含完成状态与集齐称号判断）。 */
    @GetMapping
    public ApiResponse<List<Map<String, Object>>> listChallenges() {
        PlayerEntity player = gameService.getCurrentPlayer();
        return ApiResponse.success(bossChallengeService.listChallenges(player.getSaveId()));
    }
}