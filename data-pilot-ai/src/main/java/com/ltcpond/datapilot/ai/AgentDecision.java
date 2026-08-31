package com.ltcpond.datapilot.ai;

import java.math.BigDecimal;
import java.util.List;

/** 模型的结构化下一步决定。type 只能是 INTENT、TOOL_CALL 或 FINAL。 */
public record AgentDecision(
        String type,
        String intent,
        String tool,
        String question,
        Integer topK,
        List<String> tableNames,
        String sql,
        String outcome,
        String questionAnalysis,
        List<String> relatedTables,
        String explanation,
        BigDecimal confidence,
        String clarificationQuestion) {

    public AgentDecision {
        tableNames = tableNames == null ? List.of() : List.copyOf(tableNames);
        relatedTables = relatedTables == null ? List.of() : List.copyOf(relatedTables);
    }
}
