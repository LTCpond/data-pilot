package com.ltcpond.datapilot.datasource.connection;

/** 外部数据源不可连接或无法读取，消息中不得包含底层连接详情。 */
public class ExternalDatasourceException extends RuntimeException {

    public ExternalDatasourceException() {
        super("External datasource operation failed");
    }
}
