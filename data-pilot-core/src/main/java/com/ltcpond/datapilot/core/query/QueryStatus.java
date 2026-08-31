package com.ltcpond.datapilot.core.query;

/** 受控只读查询 Agent 工作流状态。 */
public enum QueryStatus {
    CREATED,
    AGENT_ROUTING,
    AGENT_RUNNING,
    AGENT_FINALIZING,
    CANCEL_REQUESTED,
    CANCELLED,
    NEEDS_CLARIFICATION,
    SUCCEEDED,
    FAILED
}
