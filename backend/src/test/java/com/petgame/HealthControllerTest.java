package com.petgame;

import com.petgame.common.ApiResponse;
import com.petgame.common.controller.HealthController;
import com.petgame.config.GameProperties;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 阶段 0 验证测试：验证统一响应结构与健康检查接口。
 */
class HealthControllerTest {

    @Test
    void health_shouldReturnSuccessWithVersion() {
        GameProperties props = new GameProperties();
        props.setVersion("1.0.0");
        HealthController controller = new HealthController(props);

        ApiResponse<Map<String, String>> response = controller.health();

        assertTrue(response.isSuccess());
        assertNotNull(response.getData());
        assertEquals("ok", response.getData().get("status"));
        assertEquals("1.0.0", response.getData().get("version"));
        assertNull(response.getMessage());
        assertNull(response.getCode());
    }

    @Test
    void apiResponse_success_shouldHaveCorrectStructure() {
        ApiResponse<String> response = ApiResponse.success("test");
        assertTrue(response.isSuccess());
        assertEquals("test", response.getData());
        assertNull(response.getMessage());
        assertNull(response.getCode());
    }

    @Test
    void apiResponse_error_shouldHaveCodeAndMessage() {
        ApiResponse<Void> response = ApiResponse.error("EXP_NOT_ENOUGH", "经验池经验不足");
        assertFalse(response.isSuccess());
        assertNull(response.getData());
        assertEquals("EXP_NOT_ENOUGH", response.getCode());
        assertEquals("经验池经验不足", response.getMessage());
    }
}
