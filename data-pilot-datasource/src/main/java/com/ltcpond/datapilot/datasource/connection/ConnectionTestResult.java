package com.ltcpond.datapilot.datasource.connection;

/** 数据源连接成功后可安全返回的数据库信息。 */
public record ConnectionTestResult(String databaseProduct, String databaseVersion) {
}
