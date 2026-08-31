package com.ltcpond.datapilot.core.query;

import java.time.LocalDateTime;

/** 可跨实例广播的安全 Agent 步骤事件。 */
public record AgentStepEvent(long queryId, AgentStepView step, LocalDateTime occurredAt) {
}
