package com.ltcpond.datapilot.core.query;

/** 确定性 Text-to-SQL 工作流状态。 */
public enum QueryStatus {
    CREATED,
    SCHEMA_PREPARING,
    SQL_GENERATING,
    SQL_VALIDATING,
    SQL_REPAIRING,
    SQL_EXECUTING,
    CANCEL_REQUESTED,
    CANCELLED,
    SUCCEEDED,
    FAILED
}
