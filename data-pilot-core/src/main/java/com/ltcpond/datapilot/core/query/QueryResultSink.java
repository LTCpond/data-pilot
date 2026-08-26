package com.ltcpond.datapilot.core.query;

import java.time.LocalDateTime;

/** 在任务进入成功终态前交付异步结果，返回其过期时间。 */
@FunctionalInterface
public interface QueryResultSink {

    LocalDateTime store(QueryResultView result);

    static QueryResultSink none() {
        return ignored -> null;
    }
}
