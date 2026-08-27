package com.ltcpond.datapilot.api.async;

import com.ltcpond.datapilot.core.query.QueryEventPublisher;
import com.ltcpond.datapilot.core.query.QueryStatusEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** 通过Redis Pub/Sub广播状态；发布失败不得破坏同步问数流程。 */
@Component
@RequiredArgsConstructor
public class RedisQueryEventPublisher implements QueryEventPublisher {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    /** 将任务状态事件发布到 Redis channel，失败时静默降级到轮询恢复。 */
    @Override
    public void publish(QueryStatusEvent event) {
        try {
            redisTemplate.convertAndSend(
                    AsyncQueryConfiguration.EVENT_CHANNEL,
                    objectMapper.writeValueAsString(event));
        } catch (RuntimeException ignored) {
            // MySQL任务状态仍是事实来源，客户端可通过重连快照恢复。
        }
    }
}
