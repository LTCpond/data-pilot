package com.ltcpond.datapilot.core.query;

/** 查询任务不存在。 */
public class QueryTaskNotFoundException extends RuntimeException {

    public QueryTaskNotFoundException() {
        super("Query task not found");
    }
}
