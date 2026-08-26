package com.ltcpond.datapilot.core.query;

import java.time.LocalDateTime;

/** 可安全推送给前端的任务状态事件，不包含业务结果或连接凭据。 */
public record QueryStatusEvent(
        long queryId,
        String status,
        String errorCode,
        boolean resultAvailable,
        LocalDateTime occurredAt) {
}
