package com.petgame.config.controller;

import com.petgame.common.ApiResponse;
import com.petgame.config.GameConfigRegistry;
import com.petgame.config.model.GameElementConfig;
import com.petgame.config.model.ItemsConfig;
import com.petgame.config.model.SkillsConfig;
import com.petgame.config.model.SystemRuleConfig;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 游戏配置查询接口。
 * <p>
 * 提供属性列表、克制倍率、系统规则等只读查询能力。
 */
@RestController
@RequestMapping("/api/game/config")
public class GameConfigController {

    private final GameConfigRegistry registry;

    public GameConfigController(GameConfigRegistry registry) {
        this.registry = registry;
    }

    /**
     * 获取全部属性列表。
     */
    @GetMapping("/elements")
    public ApiResponse<List<GameElementConfig>> getElements() {
        return ApiResponse.success(registry.getElementsConfig().getElements());
    }

    /**
     * 查询两个属性之间的克制倍率。
     *
     * @param attacker 攻击方属性 ID
     * @param defender 防御方属性 ID
     */
    @GetMapping("/advantage")
    public ApiResponse<Map<String, Object>> getAdvantageMultiplier(
            @RequestParam String attacker,
            @RequestParam String defender) {

        if (registry.getElement(attacker) == null) {
            return ApiResponse.error("INVALID_ELEMENT", "不存在的属性: " + attacker);
        }
        if (registry.getElement(defender) == null) {
            return ApiResponse.error("INVALID_ELEMENT", "不存在的属性: " + defender);
        }

        double multiplier = registry.getElementAdvantageMultiplier(attacker, defender);
        Map<String, Object> data = Map.of(
                "attacker", attacker,
                "defender", defender,
                "multiplier", multiplier
        );
        return ApiResponse.success(data);
    }

    /**
     * 获取系统规则配置摘要。
     */
    @GetMapping("/system")
    public ApiResponse<SystemRuleConfig> getSystemRules() {
        return ApiResponse.success(registry.getSystemRules());
    }

    /**
     * 获取技能与被动配置（阶段 3，供战斗页面展示技能名称/描述/冷却）。
     */
    @GetMapping("/skills")
    public ApiResponse<SkillsConfig> getSkills() {
        return ApiResponse.success(registry.getSkillsConfig());
    }

    /**
     * 获取道具配置（阶段 4，供背包页面展示道具名称/描述/分类）。
     */
    @GetMapping("/items")
    public ApiResponse<ItemsConfig> getItems() {
        return ApiResponse.success(registry.getItemsConfig());
    }
}
