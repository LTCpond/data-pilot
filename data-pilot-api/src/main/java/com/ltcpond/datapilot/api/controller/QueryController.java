package com.ltcpond.datapilot.api.controller;

import com.ltcpond.datapilot.common.api.ApiResponse;
import com.ltcpond.datapilot.core.query.QueryService;
import com.ltcpond.datapilot.core.query.QueryTaskView;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 问数任务详情和历史记录接口。 */
@RestController
@RequiredArgsConstructor
public class QueryController {

    private final QueryService queryService;

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
