package com.ltcpond.datapilot.common.api;

/**
 * 统一 API 响应结构。
 *
 * @param code    业务状态码
 * @param message 面向调用方的简短信息
 * @param data    响应数据
 */
public record ApiResponse<T>(int code, String message, T data) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(0, "success", data);
    }

    public static <T> ApiResponse<T> unavailable(T data) {
        return new ApiResponse<>(503, "service unavailable", data);
    }

    public static <T> ApiResponse<T> error(int code, String message) {
        return new ApiResponse<>(code, message, null);
    }
}
