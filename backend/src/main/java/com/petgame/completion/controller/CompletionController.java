package com.petgame.completion.controller;

import com.petgame.common.ApiResponse;
import com.petgame.completion.service.CompletionService;
import com.petgame.player.entity.PlayerEntity;
import com.petgame.player.service.GameService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 游戏完成度 REST 接口（阶段 11，需求 §111）。
 */
@RestController
@RequestMapping("/api/completion")
public class CompletionController {

    private final CompletionService completionService;
    private final GameService gameService;

    public CompletionController(CompletionService completionService, GameService gameService) {
        this.completionService = completionService;
        this.gameService = gameService;
    }

    /** 游戏完成度（总体 + 各分项进度与权重）。 */
    @GetMapping
    public ApiResponse<Map<String, Object>> getCompletion() {
        PlayerEntity player = gameService.getCurrentPlayer();
        return ApiResponse.success(completionService.getCompletion(player.getSaveId()));
    }
}