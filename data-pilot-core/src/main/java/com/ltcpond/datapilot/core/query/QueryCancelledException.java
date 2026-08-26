package com.ltcpond.datapilot.core.query;

/** 任务收到取消请求并停止后续模型或 SQL 步骤。 */
public class QueryCancelledException extends RuntimeException {

    public QueryCancelledException() {
        super("Query task was cancelled");
    }
}
