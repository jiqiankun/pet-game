package com.petgame.shop.controller;

import com.petgame.common.ApiResponse;
import com.petgame.shop.service.ShopService;
import org.springframework.web.bind.annotation.*;

/**
 * 商店 REST 接口（阶段 10）。
 */
@RestController
@RequestMapping("/api/shop")
public class ShopController {

    private final ShopService shopService;

    public ShopController(ShopService shopService) {
        this.shopService = shopService;
    }

    /**
     * 查询商店商品列表（含解锁状态）。
     */
    @GetMapping
    public ApiResponse<ShopService.ShopView> getShop() {
        return ApiResponse.success(shopService.getShopView());
    }

    /**
     * 购买商品。
     */
    @PostMapping("/buy")
    public ApiResponse<ShopService.BuyResult> buyItem(@RequestBody BuyRequest request) {
        return ApiResponse.success(shopService.buyItem(request.getItemId(), request.getQuantity()));
    }

    @lombok.Data
    public static class BuyRequest {
        private String itemId;
        private int quantity = 1;
    }
}
