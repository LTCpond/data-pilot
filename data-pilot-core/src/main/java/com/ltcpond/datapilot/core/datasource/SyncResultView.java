package com.ltcpond.datapilot.core.datasource;

import java.time.LocalDateTime;

/** 一次 Schema 同步的统计结果。 */
public record SyncResultView(
        long datasourceId,
        int tableCount,
        int columnCount,
        int primaryKeyCount,
        int foreignKeyCount,
        LocalDateTime syncedAt,
        String ragStatus,
        int ragDocumentCount) {
}
