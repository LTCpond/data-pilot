package com.ltcpond.datapilot.core.datasource;

/** 连接成功后返回给 API 的安全信息。 */
public record ConnectionTestView(
        boolean reachable,
        String databaseProduct,
        String databaseVersion) {
}
