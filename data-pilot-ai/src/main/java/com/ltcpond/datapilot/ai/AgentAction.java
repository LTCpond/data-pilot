package com.ltcpond.datapilot.ai;

import java.math.BigDecimal;
import java.util.List;

/** 模型通过严格 Function Calling 返回的单个 Agent 动作。 */
public sealed interface AgentAction permits
        AgentAction.AcceptQuery,
        AgentAction.RequestClarification,
        AgentAction.RejectUnsupported,
        AgentAction.SearchSchema,
        AgentAction.GetSchema,
        AgentAction.ExecuteReadonlySql,
        AgentAction.FinishAnswer {

    /** 接受当前问题并返回脱离历史仍可独立理解的完整问题。 */
    record AcceptQuery(String resolvedQuestion) implements AgentAction {
    }

    /** 请求用户补充查询条件。 */
    record RequestClarification(
            String questionAnalysis,
            String clarificationQuestion) implements AgentAction {
    }

    /** 拒绝当前只读数据源无法回答的问题。 */
    record RejectUnsupported() implements AgentAction {
    }

    /** 使用业务语义检索候选表。 */
    record SearchSchema(String retrievalQuery, Integer topK) implements AgentAction {
    }

    /** 读取指定真实表的结构。 */
    record GetSchema(List<String> tableNames) implements AgentAction {

        public GetSchema {
            tableNames = tableNames == null ? List.of() : List.copyOf(tableNames);
        }
    }

    /** 请求应用校验并执行一条只读 SQL。 */
    record ExecuteReadonlySql(String sql) implements AgentAction {
    }

    /** 基于最后一次成功查询完成任务；真实 SQL 和数据仍由应用提供。 */
    record FinishAnswer(
            String questionAnalysis,
            List<String> relatedTables,
            String explanation,
            BigDecimal confidence) implements AgentAction {

        public FinishAnswer {
            relatedTables = relatedTables == null ? List.of() : List.copyOf(relatedTables);
        }
    }
}
