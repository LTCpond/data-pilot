package com.ltcpond.datapilot.common.api;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 统一业务响应码。
 *
 * <p>业务码用于客户端稳定识别错误，HTTP 状态用于协议语义，中文消息用于安全展示。</p>
 */
@Getter
@RequiredArgsConstructor
public enum ResponseCode {

    INVALID_REQUEST(40000, 400, "请求参数无效"),
    INVALID_DATASOURCE_CONFIGURATION(40001, 400, "数据源配置无效"),

    RESOURCE_NOT_FOUND(40400, 404, "请求资源不存在"),
    DATASOURCE_NOT_FOUND(40401, 404, "数据源不存在"),
    QUERY_TASK_NOT_FOUND(40402, 404, "查询任务不存在"),

    DUPLICATE_DATASOURCE_NAME(40901, 409, "数据源名称已存在"),
    DATASOURCE_SCHEMA_NOT_READY(40902, 409, "数据源 Schema 尚未就绪"),
    QUERY_TASK_CANCELLED(40904, 409, "查询任务已取消"),

    QUERY_RESULT_EXPIRED(41001, 410, "查询结果已过期"),
    QUERY_REJECTED(42201, 422, "问题或生成的 SQL 已被拒绝"),
    ASYNC_QUERY_QUEUE_FULL(42901, 429, "异步查询队列已满"),

    DATASOURCE_UNREACHABLE(50201, 502, "数据源无法连接"),
    DATASOURCE_METADATA_SYNC_FAILED(50202, 502, "数据源元数据同步失败"),
    SCHEMA_VECTOR_INDEX_FAILED(50203, 502, "Schema 向量索引失败"),
    AI_SQL_GENERATION_FAILED(50204, 502, "AI SQL 生成失败"),
    READ_ONLY_QUERY_EXECUTION_FAILED(50205, 502, "只读查询执行失败"),
    EXTERNAL_DATASOURCE_OPERATION_FAILED(50206, 502, "外部数据源操作失败"),

    AI_MODEL_UNAVAILABLE(50301, 503, "AI 模型不可用"),
    ASYNC_QUERY_SERVICE_UNAVAILABLE(50302, 503, "异步查询服务不可用"),
    QUERY_RESULT_DELIVERY_FAILED(50303, 503, "查询结果交付失败"),

    INTERNAL_ERROR(50000, 500, "服务器内部错误");

    private final int code;
    private final int httpStatus;
    private final String message;
}
