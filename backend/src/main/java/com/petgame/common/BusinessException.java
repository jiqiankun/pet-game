package com.petgame.common;

import lombok.Getter;

/**
 * 业务异常。
 * <p>
 * 携带稳定 errorCode，由 GlobalExceptionHandler 统一捕获并转为 ApiResponse。
 */
@Getter
public class BusinessException extends RuntimeException {

    private final String errorCode;

    public BusinessException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
