package com.ltcpond.datapilot.ai;

/** 单次模型调用的脱敏指标，不包含 Prompt、模型原文或密钥。 */
public record AiCallMetrics(
        String model,
        String promptVersion,
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens,
        long durationMs) {
}
