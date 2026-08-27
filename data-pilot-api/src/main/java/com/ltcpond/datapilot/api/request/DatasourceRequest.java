package com.ltcpond.datapilot.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**  MySQL 数据源请求。 */
public record DatasourceRequest(
        @NotBlank @Size(max = 128) String name,
        @Size(max = 512) String description,
        @NotBlank @Size(max = 1024) String jdbcUrl,
        @NotBlank @Size(max = 128) String username,
        @NotBlank @Size(max = 512) String password) {
}
