package com.ltcpond.datapilot.datasource.connection;

/** 创建临时只读连接池所需的最小连接信息。 */
public record DatasourceConnectionInfo(String jdbcUrl, String username, String password) {
}
