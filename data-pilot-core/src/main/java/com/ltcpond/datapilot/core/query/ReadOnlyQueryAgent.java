package com.ltcpond.datapilot.core.query;

import com.ltcpond.datapilot.ai.AgentDecision;
import com.ltcpond.datapilot.ai.AgentObservation;
import com.ltcpond.datapilot.ai.AgentTurnOutcome;
import com.ltcpond.datapilot.ai.AgentTurnRequest;
import com.ltcpond.datapilot.ai.AiCallMetrics;
import com.ltcpond.datapilot.ai.DataPilotAiProperties;
import com.ltcpond.datapilot.ai.QueryAgentModel;
import com.ltcpond.datapilot.common.api.ResponseCode;
import com.ltcpond.datapilot.common.exception.AppException;
import com.ltcpond.datapilot.core.datasource.DatasourceSchemaView;
import com.ltcpond.datapilot.core.datasource.SchemaTableView;
import com.ltcpond.datapilot.datasource.connection.DatasourceConnectionInfo;
import com.ltcpond.datapilot.datasource.crypto.CredentialCipher;
import com.ltcpond.datapilot.datasource.entity.AgentStepEntity;
import com.ltcpond.datapilot.datasource.entity.DatasourceEntity;
import com.ltcpond.datapilot.datasource.entity.QueryAttemptEntity;
import com.ltcpond.datapilot.datasource.entity.QueryTaskEntity;
import com.ltcpond.datapilot.datasource.query.QueryExecutionResult;
import com.ltcpond.datapilot.datasource.query.ReadOnlyQueryExecutor;
import com.ltcpond.datapilot.datasource.store.QueryTaskStore;
import com.ltcpond.datapilot.sql.SqlValidationRequest;
import com.ltcpond.datapilot.sql.SqlValidationResult;
import com.ltcpond.datapilot.sql.SqlValidator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** 在严格回合、时间和工具边界内执行单个只读查询 Agent。 */
@Component
public class ReadOnlyQueryAgent {

    private static final int MAX_TABLES_PER_CALL = 6;
    private static final int MAX_TEXT_LENGTH = 10_000;
    private static final int MAX_SUMMARY_LENGTH = 2_000;
    private static final int MAX_REASON_LENGTH = 512;
    private static final Set<String> QUERY_INTENTS = Set.of(
            "FETCH", "TREND", "COMPARISON", "RANKING");
    private static final Set<String> TOOLS = Set.of(
            "search_schema", "get_schema", "execute_readonly_sql");

    private final QueryAgentModel model;
    private final DataPilotAiProperties properties;
    private final SchemaRetriever schemaRetriever;
    private final SchemaPromptBuilder schemaPromptBuilder;
    private final SqlValidator sqlValidator;
    private final ReadOnlyQueryExecutor queryExecutor;
    private final CredentialCipher credentialCipher;
    private final QueryTaskStore taskStore;
    private final QueryStateMachine stateMachine;
    private final QueryResultSink resultSink;
    private final AgentStepEventPublisher eventPublisher;

    /** 使用 Spring 依赖创建只读查询 Agent，并为可选事件发布器提供降级实现。 */
    @Autowired
    public ReadOnlyQueryAgent(
            QueryAgentModel model,
            DataPilotAiProperties properties,
            SchemaRetriever schemaRetriever,
            SchemaPromptBuilder schemaPromptBuilder,
            SqlValidator sqlValidator,
            ReadOnlyQueryExecutor queryExecutor,
            CredentialCipher credentialCipher,
            QueryTaskStore taskStore,
            QueryStateMachine stateMachine,
            QueryResultSink resultSink,
            ObjectProvider<AgentStepEventPublisher> eventPublisherProvider) {
        this(model, properties, schemaRetriever, schemaPromptBuilder, sqlValidator, queryExecutor,
                credentialCipher, taskStore, stateMachine, resultSink,
                eventPublisherProvider.getIfAvailable(() -> ignored -> { }));
    }

    /** 创建可显式注入事件发布器的 Agent 实例，供内部装配和测试使用。 */
    ReadOnlyQueryAgent(
            QueryAgentModel model,
            DataPilotAiProperties properties,
            SchemaRetriever schemaRetriever,
            SchemaPromptBuilder schemaPromptBuilder,
            SqlValidator sqlValidator,
            ReadOnlyQueryExecutor queryExecutor,
            CredentialCipher credentialCipher,
            QueryTaskStore taskStore,
            QueryStateMachine stateMachine,
            QueryResultSink resultSink,
            AgentStepEventPublisher eventPublisher) {
        this.model = model;
        this.properties = properties;
        this.schemaRetriever = schemaRetriever;
        this.schemaPromptBuilder = schemaPromptBuilder;
        this.sqlValidator = sqlValidator;
        this.queryExecutor = queryExecutor;
        this.credentialCipher = credentialCipher;
        this.taskStore = taskStore;
        this.stateMachine = stateMachine;
        this.resultSink = resultSink;
        this.eventPublisher = eventPublisher;
    }

    /** 执行完整 Agent 循环；成功结果中的 SQL 和数据只来自最后一次成功工具调用。 */
    public QueryResultView execute(
            QueryTaskEntity task,
            DatasourceEntity datasource,
            DatasourceSchemaView fullSchema,
            Instant startedAt) {
        // 每个任务使用独立上下文，避免数据源、观察和失败计数相互污染。
        Context context = new Context(task, datasource, fullSchema, startedAt);
        // 先进入路由阶段，再开始受最大回合数约束的模型循环。
        stateMachine.transition(task, QueryStatus.AGENT_ROUTING);

        for (int turn = 1; turn <= properties.getMaximumAgentTurns(); turn++) {
            // 模型调用前后都检查取消和总超时，避免取消请求继续消耗资源。
            checkBoundary(context);
            AgentTurnOutcome modelOutcome = model.next(new AgentTurnRequest(
                    task.getQuestion(), turn, context.intent, context.observations));
            checkBoundary(context);
            AgentDecision decision = modelOutcome.decision();

            // 首轮只能处理结构化意图，不能直接执行工具或声明查询成功。
            if (turn == 1) {
                handleIntent(context, decision, modelOutcome.metrics());
                if (isTerminal(task.getStatus())) {
                    return null;
                }
                continue;
            }

            // 后续回合只接受最终动作或白名单工具调用。
            String type = normalized(decision.type());
            if ("FINAL".equals(type)) {
                QueryResultView result = handleFinal(context, decision, modelOutcome.metrics());
                if (result != null || isTerminal(task.getStatus())) {
                    return result;
                }
                continue;
            }
            if (!"TOOL_CALL".equals(type)) {
                protocolFailure(context, modelOutcome.metrics(), "Agent 返回了无效动作");
            }
            handleTool(context, decision, modelOutcome);
        }
        // 回合耗尽后稳定终止，防止模型形成无限循环。
        terminate(context, "AGENT_MAX_TURNS");
        throw new AppException(ResponseCode.READ_ONLY_QUERY_EXECUTION_FAILED);
    }

    /** 校验并处理首轮意图，必要时直接进入澄清或不可回答终态。 */
    private void handleIntent(Context context, AgentDecision decision, AiCallMetrics metrics) {
        if (!"INTENT".equals(normalized(decision.type()))) {
            protocolFailure(context, metrics, "首回合未返回意图判断");
        }
        String intent = normalized(decision.intent());
        LocalDateTime now = LocalDateTime.now();
        // 轨迹只记录标准化意图和调用指标，不保存模型原始输出。
        persistStep(context, "INTENT", null, "SUCCEEDED", "识别意图：" + safe(intent),
                null, metrics.durationMs(), metrics, now, LocalDateTime.now());
        // 模糊问题以澄清终态结束，补充问题时由用户创建新任务。
        if ("AMBIGUOUS".equals(intent)) {
            String question = sanitize(decision.clarificationQuestion(), 1_000);
            if (question == null || question.isBlank()) {
                question = "请补充要查询的指标、范围或时间条件。";
            }
            context.task.setClarificationQuestion(question);
            context.task.setQuestionAnalysis(sanitize(decision.questionAnalysis(), MAX_TEXT_LENGTH));
            context.task.setDurationMs(elapsed(context.startedAt));
            stateMachine.transition(context.task, QueryStatus.NEEDS_CLARIFICATION);
            return;
        }
        // 不可回答问题不进入 Schema 或 SQL 工具循环。
        if ("UNSUPPORTED".equals(intent)) {
            terminate(context, "QUESTION_NOT_ANSWERABLE");
            throw new AppException(ResponseCode.QUERY_REJECTED);
        }
        if (!QUERY_INTENTS.contains(intent)) {
            protocolFailure(context, metrics, "Agent 返回了未知意图");
        }
        context.intent = intent;
        stateMachine.transition(context.task, QueryStatus.AGENT_RUNNING);
    }

    /** 分派白名单工具、记录安全轨迹，并根据失败次数决定重规划或终止。 */
    private void handleTool(Context context, AgentDecision decision, AgentTurnOutcome modelOutcome) {
        String tool = normalizedLower(decision.tool());
        // 模型不能扩展工具集合，也不能指定当前任务之外的数据源。
        if (!TOOLS.contains(tool)) {
            protocolFailure(context, modelOutcome.metrics(), "Agent 请求了未授权工具");
        }
        Instant toolStarted = Instant.now();
        // 工具参数由应用校验，工具执行仍完全掌握在应用侧。
        ToolResult result = switch (tool) {
            case "search_schema" -> searchSchema(context, decision.topK());
            case "get_schema" -> getSchema(context, decision.tableNames());
            case "execute_readonly_sql" -> executeSql(context, decision.sql(), modelOutcome.metrics());
            default -> throw new IllegalStateException("不可达工具分支");
        };
        long durationMs = elapsed(toolStarted);
        // 先持久化安全摘要，再将截断后的观察加入下一回合上下文。
        persistStep(context, "TOOL", tool, result.success ? "SUCCEEDED" : "FAILED",
                result.summary, result.errorKind, durationMs, modelOutcome.metrics(),
                toLocal(toolStarted), LocalDateTime.now());
        context.observations.add(new AgentObservation(
                context.stepNo - 1, tool, result.success,
                clip(result.observation, properties.getToolObservationMaxChars()), result.errorKind));

        if (result.success) {
            return;
        }
        // 按总失败数和“工具名:错误类型”同时限制重复试错。
        context.totalFailures++;
        String signature = tool + ":" + result.errorKind;
        int sameFailures = context.failureCounts.merge(signature, 1, Integer::sum);
        if (result.fatal
                || sameFailures >= properties.getMaximumSameFailure()
                || context.totalFailures >= properties.getMaximumTotalToolFailures()) {
            terminate(context, result.errorKind);
            throw new AppException(ResponseCode.READ_ONLY_QUERY_EXECUTION_FAILED);
        }
        // 同类错误第二次出现时显式要求模型更换查询方案。
        if (sameFailures == 2) {
            String summary = "相同失败再次出现，要求 Agent 更换查询方案";
            persistStep(context, "REPLAN", null, "SUCCEEDED", summary, result.errorKind,
                    0L, null, LocalDateTime.now(), LocalDateTime.now());
            context.observations.add(new AgentObservation(
                    context.stepNo - 1, "system_replan", false, summary, result.errorKind));
        }
    }

    /** 复用 Schema RAG 检索候选表，并保存本次检索指标。 */
    private ToolResult searchSchema(Context context, Integer topK) {
        // 候选表检索复用现有 Schema RAG，并自动绑定当前任务的数据源。
        SchemaRetrievalResult retrieval = schemaRetriever.retrieve(
                context.datasource, context.fullSchema, context.task.getQuestion(), topK);
        context.retrieval = retrieval;
        String schemaPrompt = schemaPromptBuilder.build(retrieval.schema());
        applyRetrieval(context.task, retrieval.view(), schemaPrompt.length());
        taskStore.updateTask(context.task);
        String tables = String.join(", ", retrieval.view().retrievedTables());
        return ToolResult.success(
                "候选表：" + tables,
                "Schema 检索完成，命中 " + retrieval.view().promptTableCount() + " 张表");
    }

    /** 返回指定真实表的受控元数据，拒绝不存在或越界的表请求。 */
    private ToolResult getSchema(Context context, List<String> requestedTables) {
        if (requestedTables == null || requestedTables.isEmpty()) {
            return ToolResult.failure("UNKNOWN_TABLE", "未指定要读取的表", tableHint(context), false);
        }
        List<String> names = requestedTables.stream().filter(name -> name != null && !name.isBlank())
                .distinct().limit(MAX_TABLES_PER_CALL).toList();
        // 从已同步的完整 Schema 构建白名单，不信任模型提供的表名。
        Map<String, SchemaTableView> allowed = new HashMap<>();
        for (SchemaTableView table : context.fullSchema.tables()) {
            allowed.put(table.name().toLowerCase(Locale.ROOT), table);
        }
        List<SchemaTableView> selected = new ArrayList<>();
        for (String name : names) {
            SchemaTableView table = allowed.get(name.toLowerCase(Locale.ROOT));
            if (table == null) {
                return ToolResult.failure("UNKNOWN_TABLE", "请求了不存在的表", tableHint(context), false);
            }
            selected.add(table);
        }
        DatasourceSchemaView schema = new DatasourceSchemaView(context.fullSchema.datasourceId(), selected);
        return ToolResult.success(schemaPromptBuilder.build(schema),
                "读取了 " + selected.size() + " 张表的字段和关联关系");
    }

    /** 校验并执行只读 SQL；完整结果留给 API，仅向模型返回受限预览。 */
    private ToolResult executeSql(Context context, String sql, AiCallMetrics metrics) {
        // SQL 执行前必须先完成 Schema 检索，避免无上下文猜测表结构。
        if (context.retrieval == null) {
            return ToolResult.failure(
                    "SCHEMA_NOT_PREPARED", "执行前必须先检索 Schema",
                    "请先调用 search_schema，再获取需要的表结构。", false);
        }
        int attemptNo = ++context.sqlAttemptNo;
        if (attemptNo > 1) {
            context.task.setRepairCount(context.task.getRepairCount() + 1);
        }
        // 使用完整 Schema 白名单和现有只读校验器生成唯一可执行 SQL。
        SqlValidationResult validation = sqlValidator.validate(new SqlValidationRequest(
                sql,
                schemaPromptBuilder.allowedTables(context.fullSchema),
                schemaPromptBuilder.allowedColumns(context.fullSchema),
                context.task.getMaxRows()));
        if (!validation.valid()) {
            String errorKind = validationErrorKind(validation.violations());
            String reason = String.join(",", validation.violations());
            insertAttempt(context, attemptNo, sql, "REJECTED", reason, metrics);
            return ToolResult.failure(errorKind, "SQL 未通过只读安全校验",
                    validationHint(context, errorKind), false);
        }
        try {
            // 只读执行器负责最大行数、JDBC 超时和 Statement 取消。
            QueryExecutionResult execution = queryExecutor.execute(
                    connectionInfo(context.datasource), validation.executableSql(),
                    context.task.getMaxRows(), context.task.getId());
            // 完整执行结果只保存在应用上下文，作为最终 API 返回的可信来源。
            context.execution = execution;
            context.executableSql = validation.executableSql();
            insertAttempt(context, attemptNo, validation.executableSql(), "VALID", null, metrics);
            // 模型只能看到列名、行数和受行数/字符数限制的数据预览。
            String preview = "columns=" + execution.columns()
                    + ", rowCount=" + execution.rows().size()
                    + ", preview=" + execution.rows().stream()
                    .limit(properties.getToolObservationMaxRows()).toList();
            return ToolResult.success(clip(preview, properties.getToolObservationMaxChars()),
                    "只读 SQL 执行成功，返回 " + execution.rows().size() + " 行");
        } catch (AppException exception) {
            if (exception.getResponseCode() != ResponseCode.READ_ONLY_QUERY_EXECUTION_FAILED) {
                throw exception;
            }
            String errorKind = exception.getDetailCode() == null ? "OTHER" : exception.getDetailCode();
            insertAttempt(context, attemptNo, validation.executableSql(),
                    "EXECUTION_FAILED", errorKind, metrics);
            // 权限和连接问题无法通过重写 SQL 修复，标记为立即终止。
            boolean fatal = "PERMISSION_DENIED".equals(errorKind) || "CONNECTION_ERROR".equals(errorKind);
            return ToolResult.failure(errorKind, "只读 SQL 执行失败",
                    executionHint(context, errorKind), fatal);
        }
    }

    /** 处理模型最终动作，并确保成功响应只能基于已执行的工具结果生成。 */
    private QueryResultView handleFinal(Context context, AgentDecision decision, AiCallMetrics metrics) {
        String outcome = normalized(decision.outcome());
        // 模型也可在工具阶段发现条件不足并请求用户澄清。
        if ("CLARIFY".equals(outcome)) {
            String question = sanitize(decision.clarificationQuestion(), 1_000);
            if (question == null || question.isBlank()) {
                protocolFailure(context, metrics, "澄清结果缺少问题");
            }
            persistStep(context, "FINAL", null, "SUCCEEDED", "需要用户补充查询条件",
                    null, metrics.durationMs(), metrics, LocalDateTime.now(), LocalDateTime.now());
            context.task.setClarificationQuestion(question);
            context.task.setQuestionAnalysis(sanitize(decision.questionAnalysis(), MAX_TEXT_LENGTH));
            context.task.setDurationMs(elapsed(context.startedAt));
            stateMachine.transition(context.task, QueryStatus.NEEDS_CLARIFICATION);
            return null;
        }
        if ("UNSUPPORTED".equals(outcome)) {
            terminate(context, "QUESTION_NOT_ANSWERABLE");
            throw new AppException(ResponseCode.QUERY_REJECTED);
        }
        if (!"ANSWER".equals(outcome)) {
            protocolFailure(context, metrics, "Agent 最终结果无效");
        }
        // 没有成功 SQL 工具结果时，禁止模型自行声明 ANSWER。
        if (context.execution == null || context.executableSql == null) {
            ToolResult failure = ToolResult.failure(
                    "AGENT_NO_EXECUTED_QUERY", "Agent 未执行查询就尝试结束",
                    "必须先调用 execute_readonly_sql 并成功后再返回答案。", false);
            handleSyntheticFailure(context, failure, metrics);
            return null;
        }

        // 最终字段中的 SQL、行数和结果均取自应用保存的成功执行结果。
        stateMachine.transition(context.task, QueryStatus.AGENT_FINALIZING);
        context.task.setQuestionAnalysis(sanitize(decision.questionAnalysis(), MAX_TEXT_LENGTH));
        context.task.setRelatedTables(String.join(",", allowedRelatedTables(decision.relatedTables(), context.fullSchema)));
        context.task.setGeneratedSql(context.executableSql);
        context.task.setExplanation(sanitize(decision.explanation(), MAX_TEXT_LENGTH));
        context.task.setConfidence(normalizeConfidence(decision.confidence()));
        context.task.setRowCount(context.execution.rows().size());
        context.task.setDurationMs(elapsed(context.startedAt));
        context.task.setErrorCode(null);
        context.task.setClarificationQuestion(null);
        persistStep(context, "FINAL", null, "SUCCEEDED", "Agent 已基于成功查询生成最终答案",
                null, metrics.durationMs(), metrics, LocalDateTime.now(), LocalDateTime.now());

        RetrievalView retrieval = context.retrieval.view();
        QueryResultView result = new QueryResultView(
                context.task.getId(), QueryStatus.SUCCEEDED.name(),
                context.task.getQuestionAnalysis(), allowedRelatedTables(decision.relatedTables(), context.fullSchema),
                context.executableSql, context.task.getExplanation(), context.task.getConfidence(),
                context.execution.columns(), context.execution.rows(), context.execution.rows().size(),
                context.task.getDurationMs(), retrieval);
        // Redis 结果写入成功后才把任务转换为 SUCCEEDED。
        context.task.setResultExpiresAt(resultSink.store(result));
        stateMachine.transition(context.task, QueryStatus.SUCCEEDED);
        return result;
    }

    /** 将模型提前声明成功转换为一次可观察的工具失败。 */
    private void handleSyntheticFailure(Context context, ToolResult result, AiCallMetrics metrics) {
        persistStep(context, "TOOL", "execute_readonly_sql", "FAILED", result.summary,
                result.errorKind, 0L, metrics, LocalDateTime.now(), LocalDateTime.now());
        context.observations.add(new AgentObservation(
                context.stepNo - 1, "execute_readonly_sql", false, result.observation, result.errorKind));
        // 将提前完成计入总失败次数，达到上限时终止循环。
        context.totalFailures++;
        if (context.totalFailures >= properties.getMaximumTotalToolFailures()) {
            terminate(context, result.errorKind);
            throw new AppException(ResponseCode.READ_ONLY_QUERY_EXECUTION_FAILED);
        }
    }

    /** 持久化模型协议错误并以稳定错误码终止任务。 */
    private void protocolFailure(Context context, AiCallMetrics metrics, String summary) {
        persistStep(context, "FINAL", null, "FAILED", summary, "AI_TOOL_CALLING_UNSUPPORTED",
                metrics == null ? 0L : metrics.durationMs(), metrics,
                LocalDateTime.now(), LocalDateTime.now());
        terminate(context, "AI_TOOL_CALLING_UNSUPPORTED");
        throw new AppException(ResponseCode.AI_SQL_GENERATION_FAILED, "AI_TOOL_CALLING_UNSUPPORTED");
    }

    /** 将尚未结束的任务安全转换为失败终态。 */
    private void terminate(Context context, String errorCode) {
        if (isTerminal(context.task.getStatus())) return;
        context.task.setErrorCode(sanitize(errorCode, 64));
        context.task.setDurationMs(elapsed(context.startedAt));
        stateMachine.transition(context.task, QueryStatus.FAILED);
    }

    /** 在模型和工具边界检查取消状态及 Agent 总墙钟超时。 */
    private void checkBoundary(Context context) {
        // 每次从持久层读取最新状态，使跨线程取消请求及时生效。
        QueryTaskEntity latest = taskStore.findTask(context.task.getId()).orElse(context.task);
        QueryStatus latestStatus = QueryStatus.valueOf(latest.getStatus());
        if (latestStatus == QueryStatus.CANCEL_REQUESTED || latestStatus == QueryStatus.CANCELLED) {
            context.task.setStatus(latest.getStatus());
            if (latestStatus == QueryStatus.CANCEL_REQUESTED) {
                stateMachine.transition(context.task, QueryStatus.CANCELLED);
            }
            throw new AppException(ResponseCode.QUERY_TASK_CANCELLED);
        }
        // 总墙钟超时覆盖多次模型调用和工具调用的累计耗时。
        if (Duration.between(context.startedAt, Instant.now()).compareTo(properties.getAgentTimeout()) > 0) {
            terminate(context, "AGENT_TIMEOUT");
            throw new AppException(ResponseCode.READ_ONLY_QUERY_EXECUTION_FAILED);
        }
    }

    /** 写入 SQL 尝试审计记录及对应模型指标。 */
    private void insertAttempt(
            Context context, int attemptNo, String sql, String outcome, String reason, AiCallMetrics metrics) {
        QueryAttemptEntity attempt = new QueryAttemptEntity();
        attempt.setTaskId(context.task.getId());
        attempt.setAttemptNo(attemptNo);
        attempt.setAttemptType("AGENT_SQL");
        attempt.setCandidateSql(sql);
        attempt.setOutcome(outcome);
        // 审计记录只保存脱敏原因，不落库 JDBC 原始异常或连接信息。
        attempt.setSanitizedReason(sanitize(reason, MAX_REASON_LENGTH));
        if (metrics != null) {
            attempt.setModelName(sanitize(metrics.model(), 128));
            attempt.setPromptVersion(sanitize(metrics.promptVersion(), 64));
            attempt.setPromptTokens(metrics.promptTokens());
            attempt.setCompletionTokens(metrics.completionTokens());
            attempt.setTotalTokens(metrics.totalTokens());
            attempt.setModelDurationMs(metrics.durationMs());
        } else {
            attempt.setModelDurationMs(0L);
        }
        attempt.setCreatedAt(LocalDateTime.now());
        taskStore.insertAttempt(attempt);
        taskStore.updateTask(context.task);
    }

    /** 先持久化脱敏 Agent 步骤，再尽力发布实时 SSE 事件。 */
    private void persistStep(
            Context context,
            String kind,
            String toolName,
            String status,
            String summary,
            String errorKind,
            Long durationMs,
            AiCallMetrics metrics,
            LocalDateTime startedAt,
            LocalDateTime completedAt) {
        AgentStepEntity entity = new AgentStepEntity();
        entity.setTaskId(context.task.getId());
        entity.setStepNo(context.stepNo++);
        entity.setKind(kind);
        entity.setToolName(toolName);
        entity.setStatus(status);
        entity.setSummary(sanitize(summary, MAX_SUMMARY_LENGTH));
        entity.setErrorKind(sanitize(errorKind, 64));
        entity.setDurationMs(durationMs);
        entity.setPromptTokens(metrics == null ? null : metrics.promptTokens());
        entity.setCompletionTokens(metrics == null ? null : metrics.completionTokens());
        entity.setStartedAt(startedAt);
        entity.setCompletedAt(completedAt);
        // 数据库轨迹是重连快照的事实来源，必须先于实时事件写入。
        taskStore.insertAgentStep(entity);
        try {
            AgentStepView view = toView(entity);
            eventPublisher.publish(new AgentStepEvent(context.task.getId(), view, LocalDateTime.now()));
        } catch (RuntimeException ignored) {
            // MySQL 是轨迹事实来源，事件发布失败可由 SSE 重连快照恢复。
        }
    }

    /** 将轨迹实体转换为不包含内部数据的接口视图。 */
    static AgentStepView toView(AgentStepEntity entity) {
        return new AgentStepView(
                entity.getId(), entity.getTaskId(), entity.getStepNo(), entity.getKind(),
                entity.getToolName(), entity.getStatus(), entity.getSummary(), entity.getErrorKind(),
                entity.getDurationMs(), entity.getPromptTokens(), entity.getCompletionTokens(),
                entity.getStartedAt(), entity.getCompletedAt());
    }

    /** 将 Schema 检索范围、耗时和提示词大小写入任务统计。 */
    private void applyRetrieval(QueryTaskEntity task, RetrievalView retrieval, int schemaPromptChars) {
        task.setRagUsed("RAG".equals(retrieval.mode()));
        task.setRagFallback(retrieval.fallback());
        task.setSchemaTableCount(retrieval.totalTableCount());
        task.setPromptTableCount(retrieval.promptTableCount());
        task.setRetrievedTables(String.join(",", retrieval.retrievedTables()));
        task.setRetrievalDurationMs(retrieval.durationMs());
        task.setSchemaPromptChars(schemaPromptChars);
    }

    /** 将 SQL 校验违规项归并为 Agent 可处理的安全错误分类。 */
    private String validationErrorKind(List<String> violations) {
        if (violations.contains("UNKNOWN_COLUMN")) return "UNKNOWN_COLUMN";
        if (violations.contains("UNAUTHORIZED_TABLE") || violations.contains("SYSTEM_SCHEMA_ACCESS")) {
            return "UNKNOWN_TABLE";
        }
        return "SYNTAX_ERROR";
    }

    /** 根据 SQL 校验错误生成不暴露内部异常的修复提示。 */
    private String validationHint(Context context, String errorKind) {
        if ("UNKNOWN_COLUMN".equals(errorKind)) {
            return "请调用 get_schema 核对列名后重写 SQL。";
        }
        if ("UNKNOWN_TABLE".equals(errorKind)) {
            return tableHint(context);
        }
        return "请只生成一条无注释的 SELECT 或 WITH...SELECT，并使用已获取的 Schema。";
    }

    /** 根据执行错误类型生成受控且可操作的重新规划提示。 */
    private String executionHint(Context context, String errorKind) {
        return switch (errorKind) {
            case "UNKNOWN_COLUMN" -> "请调用 get_schema 核对字段。";
            case "UNKNOWN_TABLE" -> tableHint(context);
            case "QUERY_TIMEOUT" -> "请缩小时间范围、减少 JOIN 或先聚合再查询。";
            case "PERMISSION_DENIED" -> "只读账户没有执行该查询的权限。";
            case "CONNECTION_ERROR" -> "数据源连接失败，SQL 修复无法解决。";
            case "TRANSIENT_ERROR" -> "数据源发生临时事务错误，请更换更简单的查询方案。";
            default -> "请依据已获取的 Schema 更换 SQL 方案。";
        };
    }

    /** 生成数量受限的真实表名提示。 */
    private String tableHint(Context context) {
        return "可用表：" + context.fullSchema.tables().stream().map(SchemaTableView::name).limit(12).toList();
    }

    /** 过滤模型返回的相关表，仅保留当前数据源中真实存在的表。 */
    private List<String> allowedRelatedTables(List<String> requested, DatasourceSchemaView schema) {
        Set<String> allowed = new LinkedHashSet<>();
        schema.tables().forEach(table -> allowed.add(table.name().toLowerCase(Locale.ROOT)));
        if (requested == null) return List.of();
        return requested.stream().filter(name -> name != null && allowed.contains(name.toLowerCase(Locale.ROOT)))
                .distinct().toList();
    }

    /** 为本次只读执行构造数据源连接信息。 */
    private DatasourceConnectionInfo connectionInfo(DatasourceEntity datasource) {
        // 密码仅在执行前解密并传给执行器，不进入轨迹或模型上下文。
        return new DatasourceConnectionInfo(
                datasource.getJdbcUrl(), datasource.getUsername(),
                credentialCipher.decrypt(datasource.getEncryptedPassword()));
    }

    /** 将模型置信度限制在零到一之间。 */
    private BigDecimal normalizeConfidence(BigDecimal confidence) {
        return confidence == null ? null : confidence.max(BigDecimal.ZERO).min(BigDecimal.ONE);
    }

    /** 判断任务是否已经进入不可继续执行的终态。 */
    private boolean isTerminal(String status) {
        return QueryStatus.SUCCEEDED.name().equals(status)
                || QueryStatus.FAILED.name().equals(status)
                || QueryStatus.CANCELLED.name().equals(status)
                || QueryStatus.NEEDS_CLARIFICATION.name().equals(status);
    }

    /** 将协议枚举文本标准化为大写形式。 */
    private String normalized(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    /** 将工具名称标准化为小写形式。 */
    private String normalizedLower(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    /** 为轨迹摘要中的空值提供安全占位文本。 */
    private String safe(String value) {
        return value == null || value.isBlank() ? "UNKNOWN" : value;
    }

    /** 去除首尾空白并限制持久化文本长度。 */
    private String sanitize(String value, int maxLength) {
        if (value == null) return null;
        String normalized = value.strip();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }

    /** 截断返回模型的观察文本，空值按空字符串处理。 */
    private String clip(String value, int maxLength) {
        String safeValue = value == null ? "" : value;
        return safeValue.length() <= maxLength ? safeValue : safeValue.substring(0, maxLength);
    }

    /** 计算从指定时刻起经过的毫秒数。 */
    private long elapsed(Instant startedAt) {
        return Duration.between(startedAt, Instant.now()).toMillis();
    }

    /** 将运行时 Instant 近似转换为当前本地时间。 */
    private LocalDateTime toLocal(Instant instant) {
        return LocalDateTime.now().minusNanos(Duration.between(instant, Instant.now()).toNanos());
    }

    private static final class Context {
        private final QueryTaskEntity task;
        private final DatasourceEntity datasource;
        private final DatasourceSchemaView fullSchema;
        private final Instant startedAt;
        private final List<AgentObservation> observations = new ArrayList<>();
        private final Map<String, Integer> failureCounts = new HashMap<>();
        private int stepNo = 1;
        private int sqlAttemptNo;
        private int totalFailures;
        private String intent;
        private SchemaRetrievalResult retrieval;
        private QueryExecutionResult execution;
        private String executableSql;

        /** 创建单次任务隔离的 Agent 运行上下文。 */
        private Context(
                QueryTaskEntity task,
                DatasourceEntity datasource,
                DatasourceSchemaView fullSchema,
                Instant startedAt) {
            this.task = task;
            this.datasource = datasource;
            this.fullSchema = fullSchema;
            this.startedAt = startedAt;
        }
    }

    private record ToolResult(
            boolean success,
            String observation,
            String summary,
            String errorKind,
            boolean fatal) {

        /** 创建成功工具结果。 */
        private static ToolResult success(String observation, String summary) {
            return new ToolResult(true, observation, summary, null, false);
        }

        /** 创建带安全错误分类和提示的失败工具结果。 */
        private static ToolResult failure(
                String errorKind, String summary, String observation, boolean fatal) {
            return new ToolResult(false, observation, summary, errorKind, fatal);
        }
    }
}
