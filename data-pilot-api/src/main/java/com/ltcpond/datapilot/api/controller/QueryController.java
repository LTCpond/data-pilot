package com.ltcpond.datapilot.api.controller;

import com.ltcpond.datapilot.api.request.QueryRequest;
import com.ltcpond.datapilot.common.api.ApiResponse;
import com.ltcpond.datapilot.core.query.QueryCommand;
import com.ltcpond.datapilot.core.query.QueryResultView;
import com.ltcpond.datapilot.core.query.QueryService;
import com.ltcpond.datapilot.core.query.QueryTaskView;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 同步问数、任务详情和历史记录接口。 */
@RestController
@RequiredArgsConstructor
public class QueryController {

    private final QueryService queryService;

    /** 同步执行一次自然语言问数请求，并在同一 HTTP 请求内返回结果。 */
    @PostMapping("/api/datasources/{datasourceId}/queries")
    public ApiResponse<QueryResultView> execute(
            @PathVariable long datasourceId,
            @Valid @RequestBody QueryRequest request) {
        return ApiResponse.success(queryService.execute(new QueryCommand(
                datasourceId, request.question(), request.maxRows())));
    }

    /** 查询单个问数任务的状态、生成 SQL、耗时和脱敏错误码。 */
    @GetMapping("/api/queries/{queryId}")
    public ApiResponse<QueryTaskView> get(@PathVariable long queryId) {
        return ApiResponse.success(queryService.get(queryId));
    }

    /** 返回某个数据源最近的问数任务列表。 */
    @GetMapping("/api/datasources/{datasourceId}/queries")
    public ApiResponse<List<QueryTaskView>> list(@PathVariable long datasourceId) {
        return ApiResponse.success(queryService.list(datasourceId));
    }
}
