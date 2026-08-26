package com.ltcpond.datapilot.core.query;

/** 状态事件发布边界，由 API 模块使用 Redis 实现。 */
@FunctionalInterface
public interface QueryEventPublisher {

    void publish(QueryStatusEvent event);
}
