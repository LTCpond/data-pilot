package com.ltcpond.datapilot.ai;

/** Agent 模型边界。模型只决定下一步，工具始终由核心模块执行。 */
public interface QueryAgentModel {

    AgentTurnOutcome next(AgentTurnRequest request);
}
