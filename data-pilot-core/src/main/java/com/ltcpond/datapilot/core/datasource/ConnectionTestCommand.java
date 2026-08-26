package com.ltcpond.datapilot.core.datasource;

/** 测试外部数据源连接的输入。 */
public record ConnectionTestCommand(String jdbcUrl, String username, String password) {
}
