package com.ltcpond.datapilot.api;

import com.ltcpond.datapilot.api.async.AsyncQueryAcceptedView;
import com.ltcpond.datapilot.api.async.AsyncQueryCoordinator;
import com.ltcpond.datapilot.api.async.AsyncQueryResultView;
import com.ltcpond.datapilot.api.async.QuerySseService;
import com.ltcpond.datapilot.api.request.CreateQueryRequest;
import com.ltcpond.datapilot.common.api.ApiResponse;
import com.ltcpond.datapilot.core.query.CreateQueryCommand;
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

    @PostMapping("/api/datasources/{datasourceId}/queries/async")
    public ResponseEntity<ApiResponse<AsyncQueryAcceptedView>> submit(
            @PathVariable long datasourceId,
            @Valid @RequestBody CreateQueryRequest request) {
        AsyncQueryAcceptedView result = coordinator.submit(new CreateQueryCommand(
                datasourceId, request.question(), request.maxRows()));
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success(result));
    }

    @GetMapping(value = "/api/queries/{queryId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(@PathVariable long queryId) {
        return sseService.connect(queryId);
    }

    @GetMapping("/api/queries/{queryId}/result")
    public ResponseEntity<ApiResponse<AsyncQueryResultView>> result(@PathVariable long queryId) {
        AsyncQueryResultView result = coordinator.result(queryId);
        HttpStatus status = switch (QueryStatus.valueOf(result.status())) {
            case SUCCEEDED -> HttpStatus.OK;
            case FAILED, CANCELLED -> HttpStatus.CONFLICT;
            default -> HttpStatus.ACCEPTED;
        };
        return ResponseEntity.status(status).body(ApiResponse.success(result));
    }

    @PostMapping("/api/queries/{queryId}/cancel")
    public ApiResponse<QueryTaskView> cancel(@PathVariable long queryId) {
        return ApiResponse.success(coordinator.cancel(queryId));
    }
}
