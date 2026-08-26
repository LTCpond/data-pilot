package com.ltcpond.datapilot.ai;

/** 修正 SQL 时只携带脱敏后的失败原因，不传递数据库异常或连接信息。 */
public record SqlRepairRequest(
        String question,
        String schema,
        String previousSql,
        String sanitizedReason) {
}
