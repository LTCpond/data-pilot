package com.ltcpond.datapilot.api;

import com.ltcpond.datapilot.api.request.CreateQueryRequest;
import com.ltcpond.datapilot.common.api.ApiResponse;
import com.ltcpond.datapilot.core.query.CreateQueryCommand;
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

    @PostMapping("/api/datasources/{datasourceId}/queries")
    public ApiResponse<QueryResultView> execute(
            @PathVariable long datasourceId,
            @Valid @RequestBody CreateQueryRequest request) {
        return ApiResponse.success(queryService.execute(new CreateQueryCommand(
                datasourceId, request.question(), request.maxRows())));
    }

    @GetMapping("/api/queries/{queryId}")
    public ApiResponse<QueryTaskView> get(@PathVariable long queryId) {
        return ApiResponse.success(queryService.get(queryId));
    }

    @GetMapping("/api/datasources/{datasourceId}/queries")
    public ApiResponse<List<QueryTaskView>> list(@PathVariable long datasourceId) {
        return ApiResponse.success(queryService.list(datasourceId));
    }
}
