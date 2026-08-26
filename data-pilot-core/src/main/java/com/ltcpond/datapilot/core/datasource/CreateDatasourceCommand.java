package com.ltcpond.datapilot.core.datasource;

/** 创建 MySQL 数据源的输入。 */
public record CreateDatasourceCommand(
        String name,
        String description,
        String jdbcUrl,
        String username,
        String password) {
}
