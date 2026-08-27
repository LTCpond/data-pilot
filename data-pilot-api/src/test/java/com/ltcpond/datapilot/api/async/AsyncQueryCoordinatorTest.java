package com.ltcpond.datapilot.api.async;

import com.ltcpond.datapilot.common.api.ResponseCode;
import com.ltcpond.datapilot.common.exception.AppException;
import com.ltcpond.datapilot.core.query.QueryCommand;
import com.ltcpond.datapilot.core.query.QueryService;
import com.ltcpond.datapilot.core.query.QueryTaskView;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AsyncQueryCoordinatorTest {

    @Test
    void shouldReturnAcceptedTaskBeforeWorkerRuns() {
        QueryService queryService = mock(QueryService.class);
        RedisQueryResultStore resultStore = mock(RedisQueryResultStore.class);
        ThreadPoolTaskExecutor executor = mock(ThreadPoolTaskExecutor.class);
        when(queryService.createAsync(any())).thenReturn(task(11L, "CREATED"));
        AsyncQueryCoordinator coordinator = new AsyncQueryCoordinator(queryService, resultStore, executor);

        AsyncQueryAcceptedView result = coordinator.submit(new QueryCommand(1L, "查询订单", 100));

        assertThat(result.queryId()).isEqualTo(11L);
        assertThat(result.eventsUrl()).isEqualTo("/api/queries/11/events");
        verify(resultStore).requireAvailable();
        verify(executor).execute(any(Runnable.class));
    }

    @Test
    void shouldDeleteCreatedTaskWhenQueueRejectsSubmission() {
        QueryService queryService = mock(QueryService.class);
        RedisQueryResultStore resultStore = mock(RedisQueryResultStore.class);
        ThreadPoolTaskExecutor executor = mock(ThreadPoolTaskExecutor.class);
        when(queryService.createAsync(any())).thenReturn(task(12L, "CREATED"));
        doThrow(new TaskRejectedException("full")).when(executor).execute(any(Runnable.class));
        AsyncQueryCoordinator coordinator = new AsyncQueryCoordinator(queryService, resultStore, executor);

        assertThatThrownBy(() -> coordinator.submit(new QueryCommand(1L, "查询订单", 100)))
                .isInstanceOfSatisfying(AppException.class, exception ->
                        assertThat(exception.getResponseCode()).isEqualTo(ResponseCode.ASYNC_QUERY_QUEUE_FULL));
        verify(queryService).discardCreatedTask(12L);
    }

    private QueryTaskView task(long id, String status) {
        return new QueryTaskView(
                id, 1L, "查询订单", "ASYNC", status, null, List.of(), null, null,
                null, 0, null, null, null, null,
                LocalDateTime.now(), null, null);
    }
}
