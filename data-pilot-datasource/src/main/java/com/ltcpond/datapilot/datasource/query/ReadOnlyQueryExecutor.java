package com.ltcpond.datapilot.datasource.query;

import com.ltcpond.datapilot.datasource.connection.DatasourceConnectionInfo;

/** 执行已经通过安全网关的只读 SQL。 */
public interface ReadOnlyQueryExecutor {

    QueryExecutionResult execute(DatasourceConnectionInfo connectionInfo, String sql, int maxRows);

    default QueryExecutionResult execute(
            DatasourceConnectionInfo connectionInfo, String sql, int maxRows, long taskId) {
        return execute(connectionInfo, sql, maxRows);
    }

    /** 尽力取消当前任务正在执行的 JDBC Statement。 */
    default void cancel(long taskId) {
    }
}
