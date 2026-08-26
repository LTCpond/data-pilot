package com.ltcpond.datapilot.core.datasource;

import java.time.LocalDateTime;

/** 不包含明文或密文密码的数据源视图。 */
public record DatasourceView(
        long id,
        String name,
        String description,
        String dbType,
        String jdbcUrl,
        String username,
        String status,
        LocalDateTime lastSyncAt,
        String ragStatus,
        String ragIndexVersion,
        int ragDocumentCount,
        LocalDateTime ragIndexedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
