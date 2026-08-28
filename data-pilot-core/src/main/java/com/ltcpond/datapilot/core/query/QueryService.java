package com.ltcpond.datapilot.core.query;

import com.ltcpond.datapilot.ai.DataPilotAiProperties;
import com.ltcpond.datapilot.ai.SqlGenerationRequest;
import com.ltcpond.datapilot.ai.SqlGenerationOutcome;
import com.ltcpond.datapilot.ai.SqlGenerationResult;
import com.ltcpond.datapilot.ai.SqlGenerator;
import com.ltcpond.datapilot.ai.SqlRepairRequest;
import com.ltcpond.datapilot.common.api.ResponseCode;
import com.ltcpond.datapilot.common.exception.AppException;
import com.ltcpond.datapilot.core.datasource.DatasourceSchemaView;
import com.ltcpond.datapilot.core.datasource.DatasourceService;
import com.ltcpond.datapilot.datasource.connection.DatasourceConnectionInfo;
import com.ltcpond.datapilot.datasource.crypto.CredentialCipher;
import com.ltcpond.datapilot.datasource.entity.DatasourceEntity;
import com.ltcpond.datapilot.datasource.entity.QueryAttemptEntity;
import com.ltcpond.datapilot.datasource.entity.QueryTaskEntity;
import com.ltcpond.datapilot.datasource.query.QueryExecutionResult;
import com.ltcpond.datapilot.datasource.query.ReadOnlyQueryExecutor;
import com.ltcpond.datapilot.datasource.store.DatasourceStore;
import com.ltcpond.datapilot.datasource.store.QueryTaskStore;
import com.ltcpond.datapilot.sql.SqlValidationRequest;
import com.ltcpond.datapilot.sql.SqlValidationResult;
import com.ltcpond.datapilot.sql.SqlValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/** 编排 Schema、Spring AI、SQL 安全网关和只读执行器的问数任务闭环。 */
@Service
@RequiredArgsConstructor
public class QueryService {

    private static final int MAX_TEXT_LENGTH = 10_000;
    private static final int MAX_REASON_LENGTH = 512;

    private final DatasourceService datasourceService;
    private final DatasourceStore datasourceStore;
    private final QueryTaskStore taskStore;
    private final CredentialCipher credentialCipher;
    private final SqlGenerator sqlGenerator;
    private final SqlValidator sqlValidator;
    private final ReadOnlyQueryExecutor queryExecutor;
    private final SchemaPromptBuilder schemaPromptBuilder;
    private final SchemaRetriever schemaRetriever;
    private final QueryStateMachine stateMachine;
    private final QueryResultSink resultSink;
    private final DataPilotAiProperties properties;

    /** 创建后台任务但不占用 HTTP 请求线程执行模型调用。 */
    public QueryTaskView createTask(QueryCommand command) {
        requiredReadyDatasource(command.datasourceId());
        if (datasourceService.schema(command.datasourceId()).tables().isEmpty()) {
            throw new AppException(ResponseCode.DATASOURCE_SCHEMA_NOT_READY);
        }
        LocalDateTime now = LocalDateTime.now();
        QueryTaskEntity task = new QueryTaskEntity();
        task.setDatasourceId(command.datasourceId());
        task.setQuestion(command.question().trim());
        task.setMaxRows(normalizeMaxRows(command.maxRows()));
        task.setStatus(QueryStatus.CREATED.name());
        task.setRepairCount(0);
        task.setRagUsed(false);
        task.setRagFallback(false);
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        return toView(taskStore.insertTask(task));
    }

    /** 执行已经持久化的任务；结果交付成功后才进入 SUCCEEDED。 */
    public QueryResultView executeTask(long taskId) {
        QueryTaskEntity task = taskStore.findTask(taskId)
                .orElseThrow(() -> new AppException(ResponseCode.QUERY_TASK_NOT_FOUND));
        // 确保任务是 CREATED 状态
        ensureCreatedOrCancel(task);
        // 确认数据源存在且状态为 READY
        DatasourceEntity datasource = requiredReadyDatasource(task.getDatasourceId());

        int maxRows = task.getMaxRows();
        // 获取数据源完整 Schema
        DatasourceSchemaView fullSchema = datasourceService.schema(task.getDatasourceId());
        if (fullSchema.tables().isEmpty()) {
            throw new AppException(ResponseCode.DATASOURCE_SCHEMA_NOT_READY);
        }
        // 记录任务开始时间，用于计算总耗时
        Instant startedAt = Instant.now();
        try {
            // 每次进入下一个状态前，检查任务是否被取消
            checkCancellation(task);
            // 进入 SCHEMA_PREPARING 状态
            stateMachine.transition(task, QueryStatus.SCHEMA_PREPARING);
            // 检索 Schema 并构建提示词
            SchemaRetrievalResult retrieval = schemaRetriever.retrieve(datasource, fullSchema, task.getQuestion());
            String schemaPrompt = schemaPromptBuilder.build(retrieval.schema());
            applyRetrieval(task, retrieval.view(), schemaPrompt.length());
            taskStore.updateTask(task);

            checkCancellation(task);
            // 进入 SQL_GENERATING 状态
            stateMachine.transition(task, QueryStatus.SQL_GENERATING);

            // 调用 AI 模型生成 SQL
            GenerationAttempt generation = generate(task.getQuestion(), schemaPrompt);

            // 记录生成尝试次数，首次为 GENERATE，后续为 REPAIR
            int attemptNo = 1;
            while (true) {
                checkCancellation(task);
                applyGeneration(task, generation.result());
                // 进入 SQL_VALIDATING 状态
                stateMachine.transition(task, QueryStatus.SQL_VALIDATING);

                if (!generation.result().answerable()) {
                    insertAttempt(task, attemptNo, attemptType(attemptNo), generation,
                            "REJECTED", "QUESTION_NOT_ANSWERABLE");
                    fail(task, "QUESTION_NOT_ANSWERABLE", startedAt);
                    throw new AppException(ResponseCode.QUERY_REJECTED);
                }

                SqlValidationResult validation = sqlValidator.validate(new SqlValidationRequest(
                        generation.result().sql(),
                        schemaPromptBuilder.allowedTables(fullSchema),
                        schemaPromptBuilder.allowedColumns(fullSchema),
                        maxRows));
                if (!validation.valid()) {
                    String reason = String.join(",", validation.violations());
                    insertAttempt(task, attemptNo, attemptType(attemptNo), generation, "REJECTED", reason);
                    if (task.getRepairCount() >= properties.getMaximumRepairAttempts()) {
                        fail(task, "SQL_VALIDATION_FAILED", startedAt);
                        throw new AppException(ResponseCode.QUERY_REJECTED);
                    }
                    generation = repair(task, task.getQuestion(), schemaPrompt, generation.result().sql(), reason);
                    attemptNo++;
                    continue;
                }

                checkCancellation(task);
                // 进入 SQL_EXECUTING 状态
                stateMachine.transition(task, QueryStatus.SQL_EXECUTING);
                try {
                    QueryExecutionResult execution = queryExecutor.execute(
                            connectionInfo(datasource), validation.executableSql(), maxRows, task.getId());
                    checkCancellation(task);
                    insertAttempt(task, attemptNo, attemptType(attemptNo), generation, "VALID", null);
                    return succeed(task, generation.result(), validation.executableSql(), execution,
                            startedAt, retrieval.view());
                } catch (AppException exception) {
                    // 出现异常，重新生成一次 SQL，若达到最大修复次数则失败
                    if (exception.getResponseCode() != ResponseCode.READ_ONLY_QUERY_EXECUTION_FAILED) {
                        throw exception;
                    }
                    String detailCode = exception.getDetailCode() == null
                            ? "QUERY_EXECUTION_FAILED"
                            : exception.getDetailCode();
                    checkCancellation(task);
                    insertAttempt(task, attemptNo, attemptType(attemptNo), generation,
                            "EXECUTION_FAILED", detailCode);
                    if (!isRepairableExecutionError(detailCode)
                            || task.getRepairCount() >= properties.getMaximumRepairAttempts()) {
                        fail(task, detailCode, startedAt);
                        throw new AppException(ResponseCode.READ_ONLY_QUERY_EXECUTION_FAILED);
                    }
                    generation = repair(task, task.getQuestion(), schemaPrompt,
                            validation.executableSql(), detailCode);
                    attemptNo++;
                }
            }
        } catch (AppException exception) {
            // 根据异常类型设置任务失败状态
            switch (exception.getResponseCode()) {
                case QUERY_RESULT_DELIVERY_FAILED -> failSafely(task, "RESULT_STORE_UNAVAILABLE", startedAt);
                case AI_MODEL_UNAVAILABLE -> failSafely(task, "AI_MODEL_UNAVAILABLE", startedAt);
                case AI_SQL_GENERATION_FAILED -> failSafely(
                        task,
                        exception.getDetailCode() == null ? "AI_GENERATION_FAILED" : exception.getDetailCode(),
                        startedAt);
                case QUERY_TASK_CANCELLED, QUERY_REJECTED, READ_ONLY_QUERY_EXECUTION_FAILED -> {
                    // 对应状态已经由当前流程持久化，不重复覆盖。
                }
                default -> {
                    failSafely(task, "QUERY_WORKFLOW_FAILED", startedAt);
                    throw new AppException(ResponseCode.READ_ONLY_QUERY_EXECUTION_FAILED);
                }
            }
            throw exception;
        } catch (RuntimeException exception) {
            failSafely(task, "QUERY_WORKFLOW_FAILED", startedAt);
            throw new AppException(ResponseCode.READ_ONLY_QUERY_EXECUTION_FAILED);
        }
    }

    /** 读取任务当前持久化状态，任务不存在时抛出稳定业务异常。 */
    public QueryTaskView get(long queryId) {
        return toView(taskStore.findTask(queryId)
                .orElseThrow(() -> new AppException(ResponseCode.QUERY_TASK_NOT_FOUND)));
    }

    /** 返回指定数据源最近的问数任务列表，并先确认数据源存在。 */
    public List<QueryTaskView> list(long datasourceId) {
        datasourceService.get(datasourceId);
        return taskStore.findTasks(datasourceId).stream().map(this::toView).toList();
    }

    /** 将运行中任务标记为取消请求，并尽力取消底层 JDBC Statement。 */
    public QueryTaskView cancel(long queryId) {
        QueryTaskEntity task = taskStore.findTask(queryId)
                .orElseThrow(() -> new AppException(ResponseCode.QUERY_TASK_NOT_FOUND));
        QueryStatus status = QueryStatus.valueOf(task.getStatus());
        if (isTerminal(status)) {
            return toView(task);
        }
        if (status != QueryStatus.CANCEL_REQUESTED) {
            stateMachine.transition(task, QueryStatus.CANCEL_REQUESTED);
        }
        queryExecutor.cancel(queryId);
        return toView(task);
    }

    /** 线程池拒绝任务时移除尚未开始的记录，避免产生孤儿任务。 */
    public void discardCreatedTask(long queryId) {
        taskStore.findTask(queryId)
                .filter(task -> QueryStatus.CREATED.name().equals(task.getStatus()))
                .ifPresent(task -> taskStore.deleteTask(queryId));
    }

    /** 应用重启不重放外部模型调用，统一终止遗留任务。 */
    public int failInterruptedTasks() {
        List<QueryTaskEntity> tasks = taskStore.findInterruptedTasks(Set.of(
                QueryStatus.SUCCEEDED.name(), QueryStatus.FAILED.name(), QueryStatus.CANCELLED.name()));
        for (QueryTaskEntity task : tasks) {
            task.setErrorCode("APPLICATION_RESTARTED");
            stateMachine.transition(task, QueryStatus.FAILED);
        }
        return tasks.size();
    }

    private DatasourceEntity requiredReadyDatasource(long datasourceId) {
        DatasourceEntity datasource = datasourceStore.findById(datasourceId)
                .orElseThrow(() -> new AppException(ResponseCode.DATASOURCE_NOT_FOUND));
        if (!"READY".equals(datasource.getStatus())) {
            throw new AppException(ResponseCode.DATASOURCE_SCHEMA_NOT_READY);
        }
        return datasource;
    }

    private GenerationAttempt generate(String question, String schemaPrompt) {
        SqlGenerationOutcome outcome = sqlGenerator.generate(new SqlGenerationRequest(question, schemaPrompt));
        return new GenerationAttempt(outcome);
    }

    private GenerationAttempt repair(
            QueryTaskEntity task,
            String question,
            String schemaPrompt,
            String previousSql,
            String reason) {
        task.setRepairCount(task.getRepairCount() + 1);
        stateMachine.transition(task, QueryStatus.SQL_REPAIRING);
        SqlGenerationOutcome outcome = sqlGenerator.repair(new SqlRepairRequest(
                question, schemaPrompt, nullToEmpty(previousSql), sanitize(reason, MAX_REASON_LENGTH)));
        return new GenerationAttempt(outcome);
    }

    private QueryResultView succeed(
            QueryTaskEntity task,
            SqlGenerationResult generation,
            String executableSql,
            QueryExecutionResult execution,
            Instant startedAt,
            RetrievalView retrieval) {
        long durationMs = elapsedMillis(startedAt);
        task.setGeneratedSql(executableSql);
        task.setRowCount(execution.rows().size());
        task.setDurationMs(durationMs);
        task.setErrorCode(null);
        QueryResultView result = new QueryResultView(
                task.getId(),
                QueryStatus.SUCCEEDED.name(),
                task.getQuestionAnalysis(),
                generation.relatedTables(),
                executableSql,
                task.getExplanation(),
                task.getConfidence(),
                execution.columns(),
                execution.rows(),
                execution.rows().size(),
                durationMs,
                retrieval);
        task.setResultExpiresAt(resultSink.store(result));
        stateMachine.transition(task, QueryStatus.SUCCEEDED);
        return result;
    }

    private void applyRetrieval(QueryTaskEntity task, RetrievalView retrieval, int schemaPromptChars) {
        task.setRagUsed("RAG".equals(retrieval.mode()));
        task.setRagFallback(retrieval.fallback());
        task.setSchemaTableCount(retrieval.totalTableCount());
        task.setPromptTableCount(retrieval.promptTableCount());
        task.setRetrievedTables(String.join(",", retrieval.retrievedTables()));
        task.setRetrievalDurationMs(retrieval.durationMs());
        task.setSchemaPromptChars(schemaPromptChars);
    }

    private void applyGeneration(QueryTaskEntity task, SqlGenerationResult result) {
        task.setQuestionAnalysis(sanitize(result.questionAnalysis(), MAX_TEXT_LENGTH));
        task.setRelatedTables(String.join(",", result.relatedTables()));
        task.setGeneratedSql(result.sql());
        task.setExplanation(sanitize(result.explanation(), MAX_TEXT_LENGTH));
        task.setConfidence(normalizeConfidence(result.confidence()));
    }

    private void insertAttempt(
            QueryTaskEntity task,
            int attemptNo,
            String attemptType,
            GenerationAttempt generation,
            String outcome,
            String reason) {
        QueryAttemptEntity attempt = new QueryAttemptEntity();
        attempt.setTaskId(task.getId());
        attempt.setAttemptNo(attemptNo);
        attempt.setAttemptType(attemptType);
        attempt.setCandidateSql(generation.result().sql());
        attempt.setOutcome(outcome);
        attempt.setSanitizedReason(sanitize(reason, MAX_REASON_LENGTH));
        attempt.setModelName(sanitize(generation.outcome().metrics().model(), 128));
        attempt.setPromptVersion(sanitize(generation.outcome().metrics().promptVersion(), 64));
        attempt.setPromptTokens(generation.outcome().metrics().promptTokens());
        attempt.setCompletionTokens(generation.outcome().metrics().completionTokens());
        attempt.setTotalTokens(generation.outcome().metrics().totalTokens());
        attempt.setModelDurationMs(generation.outcome().metrics().durationMs());
        attempt.setCreatedAt(LocalDateTime.now());
        taskStore.insertAttempt(attempt);
    }

    private void fail(QueryTaskEntity task, String errorCode, Instant startedAt) {
        if (QueryStatus.FAILED.name().equals(task.getStatus())
                || QueryStatus.SUCCEEDED.name().equals(task.getStatus())) {
            return;
        }
        task.setErrorCode(sanitize(errorCode, 64));
        task.setDurationMs(elapsedMillis(startedAt));
        stateMachine.transition(task, QueryStatus.FAILED);
    }

    private void failSafely(QueryTaskEntity task, String errorCode, Instant startedAt) {
        try {
            fail(task, errorCode, startedAt);
        } catch (RuntimeException ignored) {
            // 失败状态持久化不能覆盖真正的模型或查询异常。
        }
    }

    private DatasourceConnectionInfo connectionInfo(DatasourceEntity datasource) {
        return new DatasourceConnectionInfo(
                datasource.getJdbcUrl(),
                datasource.getUsername(),
                credentialCipher.decrypt(datasource.getEncryptedPassword()));
    }

    private int normalizeMaxRows(Integer requested) {
        int maxRows = requested == null ? properties.getDefaultMaxRows() : requested;
        return Math.max(1, Math.min(maxRows, properties.getAbsoluteMaxRows()));
    }

    private BigDecimal normalizeConfidence(BigDecimal confidence) {
        if (confidence == null) {
            return null;
        }
        return confidence.max(BigDecimal.ZERO).min(BigDecimal.ONE);
    }

    private QueryTaskView toView(QueryTaskEntity task) {
        return new QueryTaskView(
                task.getId(),
                task.getDatasourceId(),
                task.getQuestion(),
                task.getStatus(),
                task.getQuestionAnalysis(),
                splitRelatedTables(task.getRelatedTables()),
                task.getGeneratedSql(),
                task.getExplanation(),
                task.getConfidence(),
                task.getRepairCount(),
                task.getRowCount(),
                task.getDurationMs(),
                task.getErrorCode(),
                retrievalView(task),
                task.getCreatedAt(),
                task.getCompletedAt(),
                task.getResultExpiresAt());
    }

    private void ensureCreatedOrCancel(QueryTaskEntity task) {
        QueryStatus status = QueryStatus.valueOf(task.getStatus());
        if (status == QueryStatus.CANCEL_REQUESTED) {
            stateMachine.transition(task, QueryStatus.CANCELLED);
            throw new AppException(ResponseCode.QUERY_TASK_CANCELLED);
        }
        if (status != QueryStatus.CREATED) {
            throw new IllegalStateException("查询任务当前不可执行");
        }
    }

    private void checkCancellation(QueryTaskEntity task) {
        QueryTaskEntity latest = taskStore.findTask(task.getId()).orElse(task);
        QueryStatus status = QueryStatus.valueOf(latest.getStatus());
        if (status != QueryStatus.CANCEL_REQUESTED && status != QueryStatus.CANCELLED) {
            return;
        }
        task.setStatus(latest.getStatus());
        if (status == QueryStatus.CANCEL_REQUESTED) {
            stateMachine.transition(task, QueryStatus.CANCELLED);
        }
        throw new AppException(ResponseCode.QUERY_TASK_CANCELLED);
    }

    private boolean isTerminal(QueryStatus status) {
        return status == QueryStatus.SUCCEEDED
                || status == QueryStatus.FAILED
                || status == QueryStatus.CANCELLED;
    }

    private RetrievalView retrievalView(QueryTaskEntity task) {
        if (task.getSchemaTableCount() == null) {
            return null;
        }
        return new RetrievalView(
                Boolean.TRUE.equals(task.getRagUsed()) ? "RAG" : "FULL_SCHEMA",
                Boolean.TRUE.equals(task.getRagFallback()),
                task.getSchemaTableCount(),
                task.getPromptTableCount() == null ? task.getSchemaTableCount() : task.getPromptTableCount(),
                splitRelatedTables(task.getRetrievedTables()),
                task.getRetrievalDurationMs() == null ? 0L : task.getRetrievalDurationMs());
    }

    private List<String> splitRelatedTables(String relatedTables) {
        if (relatedTables == null || relatedTables.isBlank()) {
            return List.of();
        }
        return Arrays.stream(relatedTables.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
    }

    private String sanitize(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String normalized = value.strip();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private long elapsedMillis(Instant startedAt) {
        return Duration.between(startedAt, Instant.now()).toMillis();
    }

    private String attemptType(int attemptNo) {
        return attemptNo == 1 ? "GENERATE" : "REPAIR";
    }

    private boolean isRepairableExecutionError(String errorCode) {
        return "INVALID_SQL".equals(errorCode)
                || "QUERY_TIMEOUT".equals(errorCode)
                || "QUERY_EXECUTION_FAILED".equals(errorCode);
    }

    private record GenerationAttempt(SqlGenerationOutcome outcome) {

        private SqlGenerationResult result() {
            return outcome.result();
        }
    }
}
