package com.ltcpond.datapilot.ai;

/** 返回给模型的脱敏工具观察，不包含凭据、原始异常或完整业务数据。 */
public record AgentObservation(
        int stepNo,
        String toolName,
        boolean success,
        String output,
        String errorKind) {
}
