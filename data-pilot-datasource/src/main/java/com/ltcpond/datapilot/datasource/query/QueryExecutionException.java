package com.ltcpond.datapilot.datasource.query;

/** 只暴露稳定错误码，避免数据库地址、SQLState 细节和驱动消息向上泄漏。 */
public class QueryExecutionException extends RuntimeException {

    private final String errorCode;

    public QueryExecutionException(String errorCode) {
        super("Read-only query execution failed");
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
