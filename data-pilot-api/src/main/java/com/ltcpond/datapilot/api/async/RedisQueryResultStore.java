package com.ltcpond.datapilot.api.async;

import com.ltcpond.datapilot.common.api.ResponseCode;
import com.ltcpond.datapilot.common.exception.AppException;
import com.ltcpond.datapilot.core.query.QueryResultSink;
import com.ltcpond.datapilot.core.query.QueryResultView;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Optional;
import tools.jackson.databind.ObjectMapper;

/** 仅按任务ID暂存结果，不跨任务复用自然语言问题。 */
@Component
@RequiredArgsConstructor
public class RedisQueryResultStore implements QueryResultSink {

    private static final String KEY_PREFIX = "data-pilot:query:result:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final AsyncQueryProperties properties;

    /** 在提交异步任务前确认 Redis 可用，避免任务完成后无处交付结果。 */
    public void requireAvailable() {
        try (RedisConnection connection = redisTemplate.getConnectionFactory().getConnection()) {
            if (connection.ping() == null) {
                throw new AppException(ResponseCode.ASYNC_QUERY_SERVICE_UNAVAILABLE);
            }
        } catch (RuntimeException exception) {
            throw new AppException(ResponseCode.ASYNC_QUERY_SERVICE_UNAVAILABLE);
        }
    }

    /** 将成功查询结果序列化到 Redis，并返回客户端可见的过期时间。 */
    @Override
    public LocalDateTime store(QueryResultView result) {
        try {
            redisTemplate.opsForValue().set(
                    key(result.queryId()),
                    objectMapper.writeValueAsString(result),
                    properties.getResultTtl());
            return LocalDateTime.now().plus(properties.getResultTtl());
        } catch (RuntimeException exception) {
            throw new AppException(ResponseCode.QUERY_RESULT_DELIVERY_FAILED);
        }
    }

    /** 按任务 ID 读取异步结果；键不存在时表示结果已过期或尚未写入。 */
    public Optional<QueryResultView> find(long queryId) {
        try {
            String json = redisTemplate.opsForValue().get(key(queryId));
            return json == null
                    ? Optional.empty()
                    : Optional.of(objectMapper.readValue(json, QueryResultView.class));
        } catch (RuntimeException exception) {
            throw new AppException(ResponseCode.ASYNC_QUERY_SERVICE_UNAVAILABLE);
        }
    }

    private String key(long queryId) {
        return KEY_PREFIX + queryId;
    }
}
