package com.ltcpond.datapilot.core.query;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** 查询任务视图 */
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
        String clarificationQuestion,
        RetrievalView retrieval,
        LocalDateTime createdAt,
        LocalDateTime completedAt,
        LocalDateTime resultExpiresAt) {
}
