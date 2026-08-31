package com.ltcpond.datapilot.api.async;

import com.ltcpond.datapilot.core.query.QueryStatusEvent;
import com.ltcpond.datapilot.core.query.AgentStepEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;

/** 异步问数线程池与Redis状态事件订阅。 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableConfigurationProperties(AsyncQueryProperties.class)
public class AsyncQueryConfiguration {

    static final String EVENT_CHANNEL = "data-pilot:query:events";
    static final String AGENT_EVENT_CHANNEL = "data-pilot:query:agent-events";

    @Bean(name = "queryTaskExecutor")
    ThreadPoolTaskExecutor queryTaskExecutor(AsyncQueryProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("data-pilot-query-");
        executor.setCorePoolSize(properties.getCorePoolSize());
        executor.setMaxPoolSize(properties.getMaxPoolSize());
        executor.setQueueCapacity(properties.getQueueCapacity());
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.initialize();
        return executor;
    }

    @Bean
    RedisMessageListenerContainer queryEventListenerContainer(
            RedisConnectionFactory connectionFactory,
            ObjectMapper objectMapper,
            QuerySseService sseService) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener((message, pattern) -> {
            try {
                String json = new String(message.getBody(), StandardCharsets.UTF_8);
                sseService.publish(objectMapper.readValue(json, QueryStatusEvent.class));
            } catch (Exception ignored) {
                // 非法或不兼容事件只在当前订阅者丢弃，不影响任务主流程。
            }
        }, new ChannelTopic(EVENT_CHANNEL));
        container.addMessageListener((message, pattern) -> {
            try {
                String json = new String(message.getBody(), StandardCharsets.UTF_8);
                sseService.publish(objectMapper.readValue(json, AgentStepEvent.class));
            } catch (Exception ignored) {
                // 不兼容轨迹事件只在当前订阅者丢弃。
            }
        }, new ChannelTopic(AGENT_EVENT_CHANNEL));
        return container;
    }
}
