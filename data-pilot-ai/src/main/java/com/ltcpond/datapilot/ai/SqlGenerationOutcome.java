package com.ltcpond.datapilot.ai;

/** 将结构化 SQL 候选与本次模型调用指标绑定，便于审计与评测。 */
public record SqlGenerationOutcome(SqlGenerationResult result, AiCallMetrics metrics) {
}
