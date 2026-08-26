package com.ltcpond.datapilot.core.query;

/** 异步结果无法安全写入临时结果存储。 */
public class QueryResultDeliveryException extends RuntimeException {

    public QueryResultDeliveryException() {
        super("Query result delivery failed");
    }
}
