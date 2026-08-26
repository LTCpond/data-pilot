package com.ltcpond.datapilot.api.async;

/** 同步任务或非成功终态没有可领取的异步结果。 */
public class QueryResultNotAvailableException extends RuntimeException {

    public QueryResultNotAvailableException() {
        super("Query result is not available");
    }
}
