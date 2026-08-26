package com.ltcpond.datapilot.api.async;

/** 异步任务成功，但15分钟临时结果已经过期。 */
public class QueryResultExpiredException extends RuntimeException {

    public QueryResultExpiredException() {
        super("Query result expired");
    }
}
