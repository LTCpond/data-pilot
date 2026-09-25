package com.ltcpond.datapilot.core.query;

/** 创建问数任务。maxRows 为空时使用服务端默认值。 */
public record QueryCommand(long datasourceId, String conversationId, String question, Integer maxRows) {
}
