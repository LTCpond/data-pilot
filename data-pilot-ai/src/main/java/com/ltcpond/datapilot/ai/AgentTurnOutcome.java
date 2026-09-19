package com.ltcpond.datapilot.ai;

/** Agent 动作和本次模型调用的安全指标。 */
public record AgentTurnOutcome(AgentAction action, AiCallMetrics metrics) {
}
