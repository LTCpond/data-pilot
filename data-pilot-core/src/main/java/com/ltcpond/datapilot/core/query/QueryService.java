package com.ltcpond.datapilot.core.query;

import com.ltcpond.datapilot.ai.DataPilotAiProperties;
import com.ltcpond.datapilot.common.api.ResponseCode;
import com.ltcpond.datapilot.common.exception.AppException;
import com.ltcpond.datapilot.core.datasource.DatasourceSchemaView;
import com.ltcpond.datapilot.core.datasource.DatasourceService;
import com.ltcpond.datapilot.datasource.entity.QueryTaskEntity;
import com.ltcpond.datapilot.datasource.entity.DatasourceEntity;
import com.ltcpond.datapilot.datasource.query.ReadOnlyQueryExecutor;
import com.ltcpond.datapilot.datasource.store.DatasourceStore;
import com.ltcpond.datapilot.datasource.store.QueryTaskStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/** 创建和管理问数任务，实际执行统一委托给受控只读查询 Agent。 */
@Service
@RequiredArgsConstructor
public class QueryService {

    private final DatasourceService datasourceService;
    private final DatasourceStore datasourceStore;
    private final QueryTaskStore taskStore;
    private final ReadOnlyQueryExecutor queryExecutor;
    private final QueryStateMachine stateMachine;
    private final ReadOnlyQueryAgent queryAgent;
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

    /** 以单 Agent 方式执行已持久化任务。 */
    public QueryResultView executeTask(long taskId) {
        QueryTaskEntity task = taskStore.findTask(taskId)
                .orElseThrow(() -> new AppException(ResponseCode.QUERY_TASK_NOT_FOUND));
        ensureCreatedOrCancel(task);
        DatasourceEntity datasource = requiredReadyDatasource(task.getDatasourceId());
        DatasourceSchemaView fullSchema = datasourceService.schema(task.getDatasourceId());
        if (fullSchema.tables().isEmpty()) {
            throw new AppException(ResponseCode.DATASOURCE_SCHEMA_NOT_READY);
        }
        Instant startedAt = Instant.now();
        try {
            return queryAgent.execute(task, datasource, fullSchema, startedAt);
        } catch (AppException exception) {
            switch (exception.getResponseCode()) {
                case QUERY_RESULT_DELIVERY_FAILED -> failSafely(task, "RESULT_STORE_UNAVAILABLE", startedAt);
                case AI_MODEL_UNAVAILABLE -> failSafely(task, "AI_MODEL_UNAVAILABLE", startedAt);
                case AI_SQL_GENERATION_FAILED -> failSafely(
                        task,
                        exception.getDetailCode() == null ? "AI_AGENT_FAILED" : exception.getDetailCode(),
                        startedAt);
                case QUERY_TASK_CANCELLED, QUERY_REJECTED, READ_ONLY_QUERY_EXECUTION_FAILED -> {
                    // Agent 已持久化对应终态。
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

    public QueryTaskView get(long queryId) {
        return toView(taskStore.findTask(queryId)
                .orElseThrow(() -> new AppException(ResponseCode.QUERY_TASK_NOT_FOUND)));
    }

    public List<QueryTaskView> list(long datasourceId) {
        datasourceService.get(datasourceId);
        return taskStore.findTasks(datasourceId).stream().map(this::toView).toList();
    }

    /** 返回持久化 Agent 轨迹，任务不存在时保持与详情接口一致的错误语义。 */
    public List<AgentStepView> steps(long queryId) {
        taskStore.findTask(queryId)
                .orElseThrow(() -> new AppException(ResponseCode.QUERY_TASK_NOT_FOUND));
        return taskStore.findAgentSteps(queryId).stream().map(ReadOnlyQueryAgent::toView).toList();
    }

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

    public void discardCreatedTask(long queryId) {
        taskStore.findTask(queryId)
                .filter(task -> QueryStatus.CREATED.name().equals(task.getStatus()))
                .ifPresent(task -> taskStore.deleteTask(queryId));
    }

    /** 应用重启不重放外部模型或工具调用，统一终止遗留任务。 */
    public int failInterruptedTasks() {
        List<QueryTaskEntity> tasks = taskStore.findInterruptedTasks(Set.of(
                QueryStatus.SUCCEEDED.name(), QueryStatus.FAILED.name(),
                QueryStatus.CANCELLED.name(), QueryStatus.NEEDS_CLARIFICATION.name()));
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

    private int normalizeMaxRows(Integer requested) {
        int maxRows = requested == null ? properties.getDefaultMaxRows() : requested;
        return Math.max(1, Math.min(maxRows, properties.getAbsoluteMaxRows()));
    }

    private QueryTaskView toView(QueryTaskEntity task) {
        return new QueryTaskView(
                task.getId(), task.getDatasourceId(), task.getQuestion(), task.getStatus(),
                task.getQuestionAnalysis(), split(task.getRelatedTables()), task.getGeneratedSql(),
                task.getExplanation(), task.getConfidence(), task.getRepairCount(), task.getRowCount(),
                task.getDurationMs(), task.getErrorCode(), task.getClarificationQuestion(),
                retrievalView(task), task.getCreatedAt(), task.getCompletedAt(), task.getResultExpiresAt());
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

    private void fail(QueryTaskEntity task, String errorCode, Instant startedAt) {
        if (isTerminal(QueryStatus.valueOf(task.getStatus()))) return;
        task.setErrorCode(sanitize(errorCode, 64));
        task.setDurationMs(Duration.between(startedAt, Instant.now()).toMillis());
        stateMachine.transition(task, QueryStatus.FAILED);
    }

    private void failSafely(QueryTaskEntity task, String errorCode, Instant startedAt) {
        try {
            fail(task, errorCode, startedAt);
        } catch (RuntimeException ignored) {
            // 失败状态持久化不能覆盖真正的模型或查询异常。
        }
    }

    private boolean isTerminal(QueryStatus status) {
        return status == QueryStatus.SUCCEEDED
                || status == QueryStatus.FAILED
                || status == QueryStatus.CANCELLED
                || status == QueryStatus.NEEDS_CLARIFICATION;
    }

    private RetrievalView retrievalView(QueryTaskEntity task) {
        if (task.getSchemaTableCount() == null) return null;
        return new RetrievalView(
                Boolean.TRUE.equals(task.getRagUsed()) ? "RAG" : "FULL_SCHEMA",
                Boolean.TRUE.equals(task.getRagFallback()), task.getSchemaTableCount(),
                task.getPromptTableCount() == null ? task.getSchemaTableCount() : task.getPromptTableCount(),
                split(task.getRetrievedTables()),
                task.getRetrievalDurationMs() == null ? 0L : task.getRetrievalDurationMs());
    }

    private List<String> split(String value) {
        if (value == null || value.isBlank()) return List.of();
        return Arrays.stream(value.split(",")).map(String::trim).filter(item -> !item.isEmpty()).toList();
    }

    private String sanitize(String value, int maxLength) {
        if (value == null) return null;
        String normalized = value.strip();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }
}
