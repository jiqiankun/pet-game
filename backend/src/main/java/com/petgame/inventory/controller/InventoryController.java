package com.petgame.inventory.controller;

import com.petgame.common.ApiResponse;
import com.petgame.inventory.service.InventoryService;
import lombok.Data;
import org.springframework.web.bind.annotation.*;

/**
 * 背包接口（阶段 4）。
 * <p>
 * 提供背包查询与恢复道具战斗外使用能力。
 * 背包不限容量，按分类（捕捉/恢复/材料/技能书/重要物品）组织，不做格子管理。
 */
@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    /**
     * 查询玩家背包（含道具配置摘要与数量）。
     */
    @GetMapping
    public ApiResponse<InventoryService.InventoryView> getInventory() {
        return ApiResponse.success(inventoryService.getInventory());
    }

    /**
     * 战斗外使用恢复道具（需求 §92）。
     * <p>
     * HEAL_HP：恢复指定宠物 HP；REVIVE：复活倒下的宠物。
     */
    @PostMapping("/use")
    public ApiResponse<InventoryService.UseItemResult> useItem(
            @RequestBody UseItemRequest request) {
        return ApiResponse.success(
                inventoryService.useRecoveryItem(request.getItemId(), request.getPetId()));
    }

    @Data
    public static class UseItemRequest {
        private String itemId;
        private Long petId;
    }
}
