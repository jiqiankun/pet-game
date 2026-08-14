package com.petgame.player.controller;

import com.petgame.common.ApiResponse;
import com.petgame.config.GameConfigRegistry;
import com.petgame.config.model.InitialPetsConfig;
import com.petgame.player.service.GameService;
import lombok.Data;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 游戏主接口。
 * <p>
 * 提供存档状态检查、新游戏创建、Bootstrap 数据聚合、手动存档等能力。
 */
@RestController
@RequestMapping("/api/game")
public class GameController {

    private final GameService gameService;
    private final GameConfigRegistry configRegistry;

    public GameController(GameService gameService, GameConfigRegistry configRegistry) {
        this.gameService = gameService;
        this.configRegistry = configRegistry;
    }

    /**
     * 检查是否存在存档。
     */
    @GetMapping("/save-status")
    public ApiResponse<Map<String, Boolean>> getSaveStatus() {
        return ApiResponse.success(Map.of("hasSave", gameService.hasSave()));
    }

    /**
     * 获取初始宠物三选一选项。
     */
    @GetMapping("/initial-pets")
    public ApiResponse<InitialPetsConfig> getInitialPets() {
        return ApiResponse.success(configRegistry.getInitialPetsConfig());
    }

    /**
     * 创建新游戏。
     */
    @PostMapping("/new-game")
    public ApiResponse<GameService.BootstrapData> createNewGame(@RequestBody NewGameRequest request) {
        if (request.getPlayerName() == null || request.getPlayerName().isBlank()) {
            return ApiResponse.error("INVALID_NAME", "玩家名称不能为空");
        }
        if (request.getPlayerName().length() > 32) {
            return ApiResponse.error("INVALID_NAME", "玩家名称不能超过 32 个字符");
        }
        if (request.getPetChoiceId() == null || request.getPetChoiceId().isBlank()) {
            return ApiResponse.error("INVALID_PET_CHOICE", "请选择初始宠物");
        }
        try {
            gameService.createNewGame(request.getPlayerName(), request.getAvatarId(), request.getPetChoiceId());
            return ApiResponse.success(gameService.getBootstrapData());
        } catch (IllegalStateException e) {
            return ApiResponse.error("SAVE_EXISTS", e.getMessage());
        } catch (IllegalArgumentException e) {
            return ApiResponse.error("INVALID_PET_CHOICE", e.getMessage());
        }
    }

    /**
     * Bootstrap：一次返回首页所需核心状态。
     */
    @GetMapping("/bootstrap")
    public ApiResponse<GameService.BootstrapData> bootstrap() {
        GameService.BootstrapData data = gameService.getBootstrapData();
        if (data == null) {
            return ApiResponse.error("NO_SAVE", "不存在存档，请先创建新游戏");
        }
        return ApiResponse.success(data);
    }

    /**
     * 手动保存（当前操作已自动持久化，此接口作为确认使用）。
     */
    @PostMapping("/save")
    public ApiResponse<Map<String, String>> manualSave() {
        // 当前所有操作已通过 @Transactional 自动持久化
        // 此接口作为手动保存的确认点
        return ApiResponse.success(Map.of("status", "saved"));
    }

    /** 查询全局难度设置。 */
    @GetMapping("/difficulty")
    public ApiResponse<GameService.DifficultyView> getDifficulty() {
        return ApiResponse.success(gameService.getDifficultyView());
    }

    /** 修改全局难度；已有 Boss 遭遇快照保持不变。 */
    @PutMapping("/difficulty")
    public ApiResponse<GameService.DifficultyView> updateDifficulty(@RequestBody DifficultyRequest request) {
        return ApiResponse.success(gameService.updateDifficulty(request.getDifficulty()));
    }

    @Data
    public static class NewGameRequest {
        private String playerName;
        private String avatarId;
        private String petChoiceId;
    }

    @Data
    public static class DifficultyRequest {
        private String difficulty;
    }
}
