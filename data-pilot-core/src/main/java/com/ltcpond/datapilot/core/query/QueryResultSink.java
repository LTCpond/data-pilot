package com.ltcpond.datapilot.core.query;

import java.time.LocalDateTime;

/** 在任务进入成功终态前交付查询结果，返回其过期时间。 */
@FunctionalInterface
public interface QueryResultSink {

    /** 交付成功查询结果，并返回该结果对客户端可领取的截止时间。 */
    LocalDateTime store(QueryResultView result);
}
