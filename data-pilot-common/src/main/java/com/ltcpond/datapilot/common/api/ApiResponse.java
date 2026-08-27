package com.ltcpond.datapilot.common.api;

/**
 * 统一 API 响应结构。
 *
 * @param code    业务状态码
 * @param message 面向调用方的简短信息
 * @param data    响应数据
 */
public record ApiResponse<T>(int code, String message, T data) {

    /** 构造业务成功响应，data 可以是结果对象或状态快照。 */
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(0, "success", data);
    }

    /** 构造服务暂不可用响应，同时保留调用方可读的诊断数据。 */
    public static <T> ApiResponse<T> unavailable(T data) {
        return new ApiResponse<>(503, "service unavailable", data);
    }

    /** 构造不带 data 的错误响应，避免把底层异常细节暴露给客户端。 */
    public static <T> ApiResponse<T> error(int code, String message) {
        return new ApiResponse<>(code, message, null);
    }
}
