package com.ltcpond.datapilot.ai;

/** Agent 决策和本次模型调用的安全指标。 */
public record AgentTurnOutcome(AgentDecision decision, AiCallMetrics metrics) {
}
