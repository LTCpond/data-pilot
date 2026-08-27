package com.ltcpond.datapilot.core.query;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** 查询历史视图，刻意不包含已经返回过的业务数据行。 */
public record QueryTaskView(
        long id,
        long datasourceId,
        String question,
        String status,
        String questionAnalysis,
        List<String> relatedTables,
        String sql,
        String explanation,
        BigDecimal confidence,
        int repairCount,
        Integer rowCount,
        Long durationMs,
        String errorCode,
        RetrievalView retrieval,
        LocalDateTime createdAt,
        LocalDateTime completedAt,
        LocalDateTime resultExpiresAt) {
}
