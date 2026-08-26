package com.ltcpond.datapilot.core.query;

/** 问题不可回答，或候选 SQL 在有限纠错后仍未通过安全校验。 */
public class QueryRejectedException extends RuntimeException {

    public QueryRejectedException() {
        super("Question or generated SQL was rejected");
    }
}
