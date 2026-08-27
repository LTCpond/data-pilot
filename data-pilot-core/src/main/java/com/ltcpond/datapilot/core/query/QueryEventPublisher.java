package com.ltcpond.datapilot.core.query;

/** 状态事件发布边界，由 API 模块使用 Redis 实现。 */
@FunctionalInterface
public interface QueryEventPublisher {

    /** 发布任务状态变更事件，供 SSE 或其他异步通知机制消费。 */
    void publish(QueryStatusEvent event);
}
