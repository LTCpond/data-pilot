package com.ltcpond.datapilot.api.async;

import com.ltcpond.datapilot.core.query.QueryResultView;

import java.time.LocalDateTime;

/** 异步结果领取响应；执行中和失败状态不包含业务结果行。 */
public record AsyncQueryResultView(
        long queryId,
        String status,
        String errorCode,
        String clarificationQuestion,
        LocalDateTime resultExpiresAt,
        QueryResultView result) {
}
