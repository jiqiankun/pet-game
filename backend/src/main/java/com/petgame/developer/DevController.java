package com.petgame.developer;

import com.petgame.config.GameProperties;
import com.petgame.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 开发者工具接口（阶段 14）。
 * <p>
 * 统一前缀 /api/dev/，仅在开发者模式开启时可用（防误触）。
 * 覆盖数据操作类开发者工具：资源、宠物、地图、Boss。
 * 战斗调试/随机数调试类接口：无敌/一击必杀/固定暴击/伤害明细/固定随机种子。
 */
@RestController
@RequestMapping("/api/dev")
public class DevController {

    private final DevService devService;
    private final GameProperties gameProperties;

    public DevController(DevService devService, GameProperties gameProperties) {
        this.devService = devService;
        this.gameProperties = gameProperties;
    }

    /** 开发者模式开关校验（未开启则拒绝所有开发者接口）。 */
    private void requireDevMode() {
        if (!gameProperties.isDeveloperMode()) {
            throw new com.petgame.common.BusinessException("DEV_MODE_DISABLED",
                    "开发者模式未开启，请在配置中设置 game.developer-mode=true");
        }
    }

    // ============================================================
    // 资源
    // ============================================================

    @PostMapping("/gold")
    public ApiResponse<Map<String, String>> grantGold(@RequestBody Map<String, Integer> body) {
        requireDevMode();
        devService.grantGold(body.getOrDefault("amount", 0));
        return ApiResponse.success(Map.of("status", "ok"));
    }

    @PostMapping("/exp")
    public ApiResponse<Map<String, String>> grantExp(@RequestBody Map<String, Integer> body) {
        requireDevMode();
        devService.grantExp(body.getOrDefault("amount", 0));
        return ApiResponse.success(Map.of("status", "ok"));
    }

    @PostMapping("/item")
    public ApiResponse<Map<String, String>> grantItem(@RequestBody Map<String, Object> body) {
        requireDevMode();
        String itemId = (String) body.get("itemId");
        int quantity = body.get("quantity") instanceof Number n ? n.intValue() : 0;
        devService.grantItem(itemId, quantity);
        return ApiResponse.success(Map.of("status", "ok"));
    }

    // ============================================================
    // 宠物
    // ============================================================

    @PostMapping("/pet")
    public ApiResponse<Map<String, Object>> addPet(@RequestBody DevService.AddPetRequest req) {
        requireDevMode();
        Long petId = devService.addPet(req);
        return ApiResponse.success(Map.of("status", "ok", "petId", petId));
    }

    @PostMapping("/pet/reset")
    public ApiResponse<Map<String, String>> resetPet(@RequestBody Map<String, Long> body) {
        requireDevMode();
        Long petId = body.get("petId");
        if (petId == null) {
            return ApiResponse.error("DEV_INVALID_PET", "petId 不能为空");
        }
        devService.resetPet(petId);
        return ApiResponse.success(Map.of("status", "ok"));
    }

    // ============================================================
    // 地图
    // ============================================================

    @PostMapping("/map/unlock")
    public ApiResponse<Map<String, String>> unlockRegion(@RequestBody Map<String, String> body) {
        requireDevMode();
        devService.unlockRegion(body.get("mapId"));
        return ApiResponse.success(Map.of("status", "ok"));
    }

    @PostMapping("/map/refresh")
    public ApiResponse<Map<String, String>> forceRefresh() {
        requireDevMode();
        devService.forceRefresh();
        return ApiResponse.success(Map.of("status", "ok"));
    }

    @PostMapping("/map/force-elite")
    public ApiResponse<Map<String, String>> forceElite() {
        requireDevMode();
        devService.forceElite();
        return ApiResponse.success(Map.of("status", "ok"));
    }

    @PostMapping("/map/force-random-event")
    public ApiResponse<Map<String, String>> forceRandomEvent() {
        requireDevMode();
        devService.forceRandomEvent();
        return ApiResponse.success(Map.of("status", "ok"));
    }

    // ============================================================
    // Boss
    // ============================================================

    @PostMapping("/boss/unlock")
    public ApiResponse<Map<String, String>> unlockBossDifficulty(@RequestBody Map<String, String> body) {
        requireDevMode();
        devService.unlockBossDifficulty(body.get("bossId"), body.get("difficulty"));
        return ApiResponse.success(Map.of("status", "ok"));
    }

    @PostMapping("/boss/direct")
    public ApiResponse<Map<String, String>> directBossDifficulty(@RequestBody Map<String, String> body) {
        requireDevMode();
        devService.directBossDifficulty(body.get("bossId"), body.get("difficulty"));
        return ApiResponse.success(Map.of("status", "ok"));
    }

    @PostMapping("/boss/defeat-count")
    public ApiResponse<Map<String, String>> setBossDefeatCount(@RequestBody Map<String, Object> body) {
        requireDevMode();
        String bossId = (String) body.get("bossId");
        String difficulty = (String) body.get("difficulty");
        int count = body.get("count") instanceof Number n ? n.intValue() : 0;
        devService.setBossDefeatCount(bossId, difficulty, count);
        return ApiResponse.success(Map.of("status", "ok"));
    }

    @PostMapping("/boss/luck")
    public ApiResponse<Map<String, String>> setBossLuck(@RequestBody Map<String, Object> body) {
        requireDevMode();
        String bossId = (String) body.get("bossId");
        int luck = body.get("luck") instanceof Number n ? n.intValue() : 0;
        devService.setBossLuck(bossId, luck);
        return ApiResponse.success(Map.of("status", "ok"));
    }

    @PostMapping("/boss/force-drop")
    public ApiResponse<Map<String, String>> forceBossDrop(@RequestBody Map<String, String> body) {
        requireDevMode();
        devService.forceBossDrop(body.get("bossId"));
        return ApiResponse.success(Map.of("status", "ok"));
    }

    // ============================================================
    // 战斗调试（阶段 14 开发者工具「战斗调试类」）
    // ============================================================

    /** 设置玩家方无敌开关。 */
    @PostMapping("/battle/invincible")
    public ApiResponse<Map<String, Object>> setPlayerInvincible(@RequestBody Map<String, Boolean> body) {
        requireDevMode();
        devService.setPlayerInvincible(Boolean.TRUE.equals(body.get("on")));
        return ApiResponse.success(devService.getBattleDebugState());
    }

    /** 设置玩家方一击必杀开关。 */
    @PostMapping("/battle/one-hit-kill")
    public ApiResponse<Map<String, Object>> setPlayerOneHitKill(@RequestBody Map<String, Boolean> body) {
        requireDevMode();
        devService.setPlayerOneHitKill(Boolean.TRUE.equals(body.get("on")));
        return ApiResponse.success(devService.getBattleDebugState());
    }

    /** 设置玩家方固定暴击开关。 */
    @PostMapping("/battle/fixed-crit")
    public ApiResponse<Map<String, Object>> setPlayerFixedCrit(@RequestBody Map<String, Boolean> body) {
        requireDevMode();
        devService.setPlayerFixedCrit(Boolean.TRUE.equals(body.get("on")));
        return ApiResponse.success(devService.getBattleDebugState());
    }

    /** 设置伤害明细/随机数调试开关。 */
    @PostMapping("/battle/debug-damage")
    public ApiResponse<Map<String, Object>> setDebugDamage(@RequestBody Map<String, Boolean> body) {
        requireDevMode();
        devService.setDebugDamage(Boolean.TRUE.equals(body.get("on")));
        return ApiResponse.success(devService.getBattleDebugState());
    }

    /** 设置下一次战斗的固定随机种子（一次性）。 */
    @PostMapping("/battle/fixed-seed")
    public ApiResponse<Map<String, Object>> setFixedBattleSeed(@RequestBody Map<String, Long> body) {
        requireDevMode();
        Long seed = body.get("seed");
        if (seed == null) {
            return ApiResponse.error("DEV_INVALID_SEED", "seed 不能为空");
        }
        devService.setFixedBattleSeed(seed);
        return ApiResponse.success(devService.getBattleDebugState());
    }

    /** 查询当前战斗调试开关状态。 */
    @GetMapping("/battle/state")
    public ApiResponse<Map<String, Object>> getBattleDebugState() {
        requireDevMode();
        return ApiResponse.success(devService.getBattleDebugState());
    }

    // ============================================================
    // 操作日志
    // ============================================================

    @GetMapping("/logs")
    public ApiResponse<List<DevOperationLogEntity>> listLogs(
            @RequestParam(value = "limit", defaultValue = "50") int limit) {
        requireDevMode();
        return ApiResponse.success(devService.listOperationLogs(limit));
    }
}