package com.ltcpond.datapilot.api.async;

import com.ltcpond.datapilot.core.query.QueryResultView;
import com.ltcpond.datapilot.core.query.RetrievalView;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import tools.jackson.databind.json.JsonMapper;

class RedisQueryResultStoreTest {

    @Test
    void shouldStoreEachTaskResultWithFifteenMinuteTtl() {
        StringRedisTemplate template = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(template.opsForValue()).thenReturn(values);
        AtomicReference<String> json = new AtomicReference<>();
        org.mockito.Mockito.doAnswer(invocation -> {
            json.set(invocation.getArgument(1));
            return null;
        }).when(values).set(eq("data-pilot:query:result:7"), anyString(), eq(Duration.ofMinutes(15)));
        when(values.get("data-pilot:query:result:7")).thenAnswer(ignored -> json.get());
        AsyncQueryProperties properties = new AsyncQueryProperties();
        RedisQueryResultStore store = new RedisQueryResultStore(
                template, JsonMapper.builder().findAndAddModules().build(), properties);

        store.store(result());

        assertThat(store.find(7L)).contains(result());
        verify(values).set(eq("data-pilot:query:result:7"), anyString(), eq(Duration.ofMinutes(15)));
    }

    private QueryResultView result() {
        return new QueryResultView(
                7L, "SUCCEEDED", "统计订单", List.of("orders"),
                "SELECT COUNT(*) FROM orders", "统计数量", new BigDecimal("0.9"),
                List.of("count"), List.of(Map.of("count", 60)), 1, 100L,
                new RetrievalView("FULL_SCHEMA", false, 5, 5, List.of("orders"), 1L));
    }
}
