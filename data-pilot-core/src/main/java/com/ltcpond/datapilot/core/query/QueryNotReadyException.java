package com.ltcpond.datapilot.core.query;

/** 数据源尚未成功同步 Schema。 */
public class QueryNotReadyException extends RuntimeException {

    public QueryNotReadyException() {
        super("Datasource is not ready for querying");
    }
}
