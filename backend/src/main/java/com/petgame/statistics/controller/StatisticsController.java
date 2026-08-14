package com.petgame.statistics.controller;

import com.petgame.common.ApiResponse;
import com.petgame.player.entity.PlayerEntity;
import com.petgame.player.service.GameService;
import com.petgame.statistics.service.StatisticsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 玩家统计 REST 接口（阶段 11，需求 §112）。
 */
@RestController
@RequestMapping("/api/statistics")
public class StatisticsController {

    private final StatisticsService statisticsService;
    private final GameService gameService;

    public StatisticsController(StatisticsService statisticsService, GameService gameService) {
        this.statisticsService = statisticsService;
        this.gameService = gameService;
    }

    /** 玩家统计总览（全部统计键 + 使用最多宠物/技能）。 */
    @GetMapping
    public ApiResponse<Map<String, Object>> getStatistics() {
        PlayerEntity player = gameService.getCurrentPlayer();
        Map<String, Object> result = new HashMap<>();
        result.put("stats", statisticsService.getAllStats(player.getSaveId()));
        result.put("mostUsed", statisticsService.computeMostUsed(player.getSaveId()));
        return ApiResponse.success(result);
    }
}