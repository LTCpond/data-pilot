package com.ltcpond.datapilot.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 连接测试请求；password 只用于本次连接，不记录日志。 */
public record ConnectionTestRequest(
        @NotBlank @Size(max = 1024) String jdbcUrl,
        @NotBlank @Size(max = 128) String username,
        @NotBlank @Size(max = 512) String password) {
}
