package com.ltcpond.datapilot.core.query;

import java.time.LocalDateTime;

/** 前端可见的 Agent 步骤，不包含模型思维链、原始异常或业务结果。 */
public record AgentStepView(
        long id,
        long queryId,
        int stepNo,
        String kind,
        String toolName,
        String status,
        String summary,
        String errorKind,
        Long durationMs,
        Integer promptTokens,
        Integer completionTokens,
        LocalDateTime startedAt,
        LocalDateTime completedAt) {
}
