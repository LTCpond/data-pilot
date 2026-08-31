package com.ltcpond.datapilot.api.async;

import com.ltcpond.datapilot.core.query.AgentStepEvent;
import com.ltcpond.datapilot.core.query.AgentStepEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** 通过 Redis Pub/Sub 广播 Agent 步骤，发布失败不影响主流程。 */
@Component
@RequiredArgsConstructor
public class RedisAgentStepEventPublisher implements AgentStepEventPublisher {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void publish(AgentStepEvent event) {
        try {
            redisTemplate.convertAndSend(
                    AsyncQueryConfiguration.AGENT_EVENT_CHANNEL,
                    objectMapper.writeValueAsString(event));
        } catch (RuntimeException ignored) {
            // MySQL 轨迹仍是事实来源，SSE 重连会恢复快照。
        }
    }
}
