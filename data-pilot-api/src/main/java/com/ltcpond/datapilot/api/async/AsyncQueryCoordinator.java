package com.ltcpond.datapilot.api.async;

import com.ltcpond.datapilot.common.api.ResponseCode;
import com.ltcpond.datapilot.common.exception.AppException;
import com.ltcpond.datapilot.core.query.QueryCommand;
import com.ltcpond.datapilot.core.query.QueryResultView;
import com.ltcpond.datapilot.core.query.QueryService;
import com.ltcpond.datapilot.core.query.QueryStatus;
import com.ltcpond.datapilot.core.query.QueryTaskView;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

/** 提交有界后台任务，并从Redis交付临时结果。 */
@Service
@RequiredArgsConstructor
public class AsyncQueryCoordinator {

    private final QueryService queryService;
    private final RedisQueryResultStore resultStore;
    @Qualifier("queryTaskExecutor")
    private final ThreadPoolTaskExecutor taskExecutor;

    /** 创建异步任务、提交到后台线程池，并返回客户端轮询和 SSE 订阅地址。 */
    public AsyncQueryAcceptedView submit(QueryCommand command) {
        resultStore.requireAvailable();
        QueryTaskView task = queryService.createAsync(command);
        try {
            taskExecutor.execute(() -> run(task.id()));
        } catch (TaskRejectedException exception) {
            queryService.discardCreatedTask(task.id());
            throw new AppException(ResponseCode.ASYNC_QUERY_QUEUE_FULL);
        }
        return new AsyncQueryAcceptedView(
                task.id(), task.status(),
                "/api/queries/" + task.id() + "/events",
                "/api/queries/" + task.id() + "/result");
    }

    /** 读取异步任务当前状态；仅成功任务会从 Redis 装载临时结果数据。 */
    public AsyncQueryResultView result(long queryId) {
        QueryTaskView task = queryService.get(queryId);
        if (!"ASYNC".equals(task.executionMode())) {
            throw new AppException(ResponseCode.QUERY_RESULT_NOT_AVAILABLE);
        }
        if (QueryStatus.SUCCEEDED.name().equals(task.status())) {
            QueryResultView result = resultStore.find(queryId)
                    .orElseThrow(() -> new AppException(ResponseCode.QUERY_RESULT_EXPIRED));
            return view(task, result);
        }
        return view(task, null);
    }

    /** 请求取消异步任务，并把取消动作委托给核心任务服务和底层执行器。 */
    public QueryTaskView cancel(long queryId) {
        return queryService.cancel(queryId);
    }

    private void run(long queryId) {
        try {
            queryService.executeTask(queryId, resultStore::store);
        } catch (RuntimeException ignored) {
            // 核心服务已持久化取消状态或脱敏错误码；后台线程不得泄露异常详情。
        }
    }

    private AsyncQueryResultView view(QueryTaskView task, QueryResultView result) {
        return new AsyncQueryResultView(
                task.id(), task.status(), task.errorCode(), task.resultExpiresAt(), result);
    }
}
