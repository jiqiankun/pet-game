package com.petgame.common.controller;

import com.petgame.common.ApiResponse;
import com.petgame.config.GameProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 健康检查接口。
 * <p>
 * 用于阶段 0 验证后端启动与前后端联通。
 */
@RestController
@RequestMapping("/api")
public class HealthController {

    private final GameProperties gameProperties;

    public HealthController(GameProperties gameProperties) {
        this.gameProperties = gameProperties;
    }

    @GetMapping("/health")
    public ApiResponse<Map<String, String>> health() {
        return ApiResponse.success(Map.of(
                "status", "ok",
                "version", gameProperties.getVersion()
        ));
    }
}
