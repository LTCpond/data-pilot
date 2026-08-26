package com.ltcpond.datapilot.datasource.store;

import java.time.LocalDateTime;

/** 元数据事务写入后的统计结果。 */
public record SyncPersistenceResult(
        int tableCount,
        int columnCount,
        int primaryKeyCount,
        int foreignKeyCount,
        LocalDateTime syncedAt) {
}
