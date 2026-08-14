package com.petgame.achievement.controller;

import com.petgame.achievement.service.AchievementService;
import com.petgame.common.ApiResponse;
import com.petgame.player.entity.PlayerEntity;
import com.petgame.player.service.GameService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 成就 REST 接口（阶段 11，需求 §110）。
 */
@RestController
@RequestMapping("/api/achievements")
public class AchievementController {

    private final AchievementService achievementService;
    private final GameService gameService;

    public AchievementController(AchievementService achievementService, GameService gameService) {
        this.achievementService = achievementService;
        this.gameService = gameService;
    }

    /** 成就列表（含解锁状态，隐藏成就未解锁时不返回）。 */
    @GetMapping
    public ApiResponse<List<Map<String, Object>>> listAchievements() {
        PlayerEntity player = gameService.getCurrentPlayer();
        return ApiResponse.success(achievementService.listAchievements(player.getSaveId()));
    }
}