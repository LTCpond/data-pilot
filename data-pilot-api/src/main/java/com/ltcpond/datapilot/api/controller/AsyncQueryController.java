package com.ltcpond.datapilot.api.controller;

import com.ltcpond.datapilot.api.async.AsyncQueryAcceptedView;
import com.ltcpond.datapilot.api.async.AsyncQueryCoordinator;
import com.ltcpond.datapilot.api.async.AsyncQueryResultView;
import com.ltcpond.datapilot.api.async.QuerySseService;
import com.ltcpond.datapilot.api.request.QueryRequest;
import com.ltcpond.datapilot.common.api.ApiResponse;
import com.ltcpond.datapilot.core.query.QueryCommand;
import com.ltcpond.datapilot.core.query.QueryStatus;
import com.ltcpond.datapilot.core.query.QueryTaskView;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** 异步问数提交、进度订阅、结果领取和取消接口。 */
@RestController
@RequiredArgsConstructor
public class AsyncQueryController {

    private final AsyncQueryCoordinator coordinator;
    private final QuerySseService sseService;

    /** 提交异步问数任务，立即返回任务 ID、事件流地址和结果领取地址。 */
    @PostMapping("/api/datasources/{datasourceId}/queries/async")
    public ResponseEntity<ApiResponse<AsyncQueryAcceptedView>> submit(
            @PathVariable long datasourceId,
            @Valid @RequestBody QueryRequest request) {
        AsyncQueryAcceptedView result = coordinator.submit(new QueryCommand(
                datasourceId, request.question(), request.maxRows()));
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success(result));
    }

    /** 打开任务状态事件流，用于前端实时展示异步查询进度。 */
    @GetMapping(value = "/api/queries/{queryId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(@PathVariable long queryId) {
        return sseService.connect(queryId);
    }

    /** 领取异步任务结果；运行中返回 202，终态任务返回当前状态快照。 */
    @GetMapping("/api/queries/{queryId}/result")
    public ResponseEntity<ApiResponse<AsyncQueryResultView>> result(@PathVariable long queryId) {
        AsyncQueryResultView result = coordinator.result(queryId);
        HttpStatus status = switch (QueryStatus.valueOf(result.status())) {
            case SUCCEEDED, FAILED, CANCELLED -> HttpStatus.OK;
            default -> HttpStatus.ACCEPTED;
        };
        return ResponseEntity.status(status).body(ApiResponse.success(result));
    }

    /** 请求取消指定异步任务，已终止任务会原样返回当前状态。 */
    @PostMapping("/api/queries/{queryId}/cancel")
    public ApiResponse<QueryTaskView> cancel(@PathVariable long queryId) {
        return ApiResponse.success(coordinator.cancel(queryId));
    }
}
