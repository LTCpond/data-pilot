package com.ltcpond.datapilot.datasource.health;

import com.ltcpond.datapilot.common.health.ComponentHealth;
import com.ltcpond.datapilot.common.health.HealthProbe;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** 通过 PING 检查应用配置的 Redis 逻辑库连接。 */
@Component
@RequiredArgsConstructor
public class RedisHealthProbe implements HealthProbe {

    private final StringRedisTemplate redisTemplate;

    /** 返回 Redis 在健康响应中的稳定组件名。 */
    @Override
    public String componentName() {
        return "redis";
    }

    /** 让 Redis 检查排在管理库检查之后。 */
    @Override
    public int order() {
        return 2;
    }

    /** 通过 PING/PONG 验证当前 Redis 逻辑库连接可用。 */
    @Override
    public ComponentHealth check() {
        String result = redisTemplate.execute((RedisCallback<String>) connection -> connection.ping());
        return "PONG".equalsIgnoreCase(result) ? ComponentHealth.up() : ComponentHealth.down();
    }
}
