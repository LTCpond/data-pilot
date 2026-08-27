package com.ltcpond.datapilot.datasource.query;

import com.ltcpond.datapilot.datasource.connection.DatasourceConnectionInfo;

/** 执行已经通过安全网关的只读 SQL。 */
public interface ReadOnlyQueryExecutor {

    /** 使用临时只读连接执行 SQL，并最多返回 maxRows 行。 */
    QueryExecutionResult execute(DatasourceConnectionInfo connectionInfo, String sql, int maxRows);

    /** 使用任务 ID 执行 SQL，便于调用方后续按任务尝试取消。 */
    default QueryExecutionResult execute(
            DatasourceConnectionInfo connectionInfo, String sql, int maxRows, long taskId) {
        return execute(connectionInfo, sql, maxRows);
    }

    /** 尽力取消当前任务正在执行的 JDBC Statement。 */
    default void cancel(long taskId) {
    }
}
