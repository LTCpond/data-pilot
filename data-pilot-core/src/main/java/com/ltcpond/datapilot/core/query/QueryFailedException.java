package com.ltcpond.datapilot.core.query;

/** 只读查询在有限纠错后仍无法执行。 */
public class QueryFailedException extends RuntimeException {

    public QueryFailedException() {
        super("Query execution failed");
    }
}
