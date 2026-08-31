package com.ltcpond.datapilot.core.query;

/** Agent 步骤事件发布边界，由 API 模块使用 Redis 实现。 */
@FunctionalInterface
public interface AgentStepEventPublisher {

    void publish(AgentStepEvent event);
}
