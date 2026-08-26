package com.ltcpond.datapilot.core.query;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** 成功问数响应；rows 只随本次响应返回，不写入管理库。 */
public record QueryResultView(
        long queryId,
        String status,
        String questionAnalysis,
        List<String> relatedTables,
        String sql,
        String explanation,
        BigDecimal confidence,
        List<String> columns,
        List<Map<String, Object>> rows,
        int rowCount,
        long durationMs,
        RetrievalView retrieval) {
}
