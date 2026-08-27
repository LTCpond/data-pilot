package com.ltcpond.datapilot.core.query;

import java.time.LocalDateTime;

/** 在任务进入成功终态前交付异步结果，返回其过期时间。 */
@FunctionalInterface
public interface QueryResultSink {

    /** 交付成功查询结果，并返回该结果对客户端可领取的截止时间。 */
    LocalDateTime store(QueryResultView result);

    /** 创建空交付器，用于同步查询这类不需要额外暂存结果的场景。 */
    static QueryResultSink none() {
        return ignored -> null;
    }
}
