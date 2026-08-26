package com.ltcpond.datapilot.ai;

import java.math.BigDecimal;
import java.util.List;

/** 模型必须返回的结构化 Text-to-SQL 结果。 */
public record SqlGenerationResult(
        boolean answerable,
        String questionAnalysis,
        List<String> relatedTables,
        String sql,
        String explanation,
        BigDecimal confidence) {

    public SqlGenerationResult {
        relatedTables = relatedTables == null ? List.of() : List.copyOf(relatedTables);
    }
}
