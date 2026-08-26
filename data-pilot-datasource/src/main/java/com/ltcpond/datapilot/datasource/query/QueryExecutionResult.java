package com.ltcpond.datapilot.datasource.query;

import java.util.List;
import java.util.Map;

/** 只存在于当前 HTTP 响应中的查询结果，不写入管理库。 */
public record QueryExecutionResult(
        List<String> columns,
        List<Map<String, Object>> rows) {

    public QueryExecutionResult {
        columns = List.copyOf(columns);
        rows = List.copyOf(rows);
    }
}
