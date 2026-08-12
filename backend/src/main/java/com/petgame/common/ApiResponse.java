package com.petgame.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * 统一 API 响应结构。
 * <p>
 * 所有接口返回此格式：
 * <pre>
 * {
 *   "success": true,
 *   "data": {},
 *   "message": null,
 *   "code": null
 * }
 * </pre>
 * 业务错误使用稳定 errorCode，前端不依赖后端异常字符串。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private boolean success;
    private T data;
    private String message;
    private String code;

    private ApiResponse() {
    }

    /**
     * 成功响应（带数据）。
     */
    public static <T> ApiResponse<T> success(T data) {
        ApiResponse<T> response = new ApiResponse<>();
        response.success = true;
        response.data = data;
        return response;
    }

    /**
     * 成功响应（无数据）。
     */
    public static <Void> ApiResponse<Void> success() {
        ApiResponse<Void> response = new ApiResponse<>();
        response.success = true;
        return response;
    }

    /**
     * 业务错误响应。
     *
     * @param code    稳定错误码，如 "EXP_NOT_ENOUGH"
     * @param message 人类可读的错误描述
     */
    public static <T> ApiResponse<T> error(String code, String message) {
        ApiResponse<T> response = new ApiResponse<>();
        response.success = false;
        response.code = code;
        response.message = message;
        return response;
    }
}
