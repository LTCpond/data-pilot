package com.ltcpond.datapilot.core.query;

import com.ltcpond.datapilot.ai.AgentDecision;
import com.ltcpond.datapilot.ai.AgentTurnOutcome;
import com.ltcpond.datapilot.ai.AiCallMetrics;
import com.ltcpond.datapilot.ai.DataPilotAiProperties;
import com.ltcpond.datapilot.ai.QueryAgentModel;
import com.ltcpond.datapilot.common.api.ResponseCode;
import com.ltcpond.datapilot.common.exception.AppException;
import com.ltcpond.datapilot.core.datasource.DatasourceSchemaView;
import com.ltcpond.datapilot.core.datasource.SchemaTableView;
import com.ltcpond.datapilot.datasource.crypto.CredentialCipher;
import com.ltcpond.datapilot.datasource.entity.AgentStepEntity;
import com.ltcpond.datapilot.datasource.entity.DatasourceEntity;
import com.ltcpond.datapilot.datasource.entity.QueryTaskEntity;
import com.ltcpond.datapilot.datasource.query.QueryExecutionResult;
import com.ltcpond.datapilot.datasource.query.ReadOnlyQueryExecutor;
import com.ltcpond.datapilot.datasource.store.QueryTaskStore;
import com.ltcpond.datapilot.sql.SqlValidationResult;
import com.ltcpond.datapilot.sql.SqlValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReadOnlyQueryAgentTest {

    private QueryAgentModel model;
    private SchemaRetriever schemaRetriever;
    private SqlValidator validator;
    private ReadOnlyQueryExecutor executor;
    private CredentialCipher cipher;
    private QueryTaskStore store;
    private QueryResultSink sink;
    private DataPilotAiProperties properties;
    private ReadOnlyQueryAgent agent;
    private QueryTaskEntity task;
    private DatasourceEntity datasource;
    private DatasourceSchemaView schema;
    private SchemaRetrievalResult retrieval;

    @BeforeEach
    void setUp() {
        model = mock(QueryAgentModel.class);
        schemaRetriever = mock(SchemaRetriever.class);
        validator = mock(SqlValidator.class);
        executor = mock(ReadOnlyQueryExecutor.class);
        cipher = mock(CredentialCipher.class);
        store = mock(QueryTaskStore.class);
        sink = mock(QueryResultSink.class);
        properties = new DataPilotAiProperties();
        properties.setMaximumAgentTurns(8);
        properties.setMaximumTotalToolFailures(4);
        properties.setMaximumSameFailure(3);

        task = new QueryTaskEntity();
        task.setId(7L);
        task.setDatasourceId(1L);
        task.setQuestion("查询订单数量");
        task.setMaxRows(100);
        task.setRepairCount(0);
        task.setStatus("CREATED");
        task.setCreatedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        datasource = new DatasourceEntity();
        datasource.setId(1L);
        datasource.setJdbcUrl("jdbc:mysql://example/demo");
        datasource.setUsername("readonly");
        datasource.setEncryptedPassword("cipher");
        schema = new DatasourceSchemaView(1L, List.of(
                new SchemaTableView(1L, "demo", "orders", "TABLE", "订单", List.of(), List.of())));
        RetrievalView retrievalView = new RetrievalView(
                "FULL_SCHEMA", false, 1, 1, List.of("orders"), 1L);
        retrieval = new SchemaRetrievalResult(schema, retrievalView);

        when(store.findTask(7L)).thenReturn(Optional.of(task));
        when(store.insertAgentStep(any())).thenAnswer(invocation -> {
            AgentStepEntity step = invocation.getArgument(0);
            step.setId(step.getStepNo().longValue());
            return step;
        });
        when(schemaRetriever.retrieve(datasource, schema, task.getQuestion(), 6)).thenReturn(retrieval);
        when(validator.validate(any())).thenReturn(SqlValidationResult.accepted(
                "SELECT COUNT(*) AS total FROM orders LIMIT 100"));
        when(cipher.decrypt("cipher")).thenReturn("secret");
        when(sink.store(any())).thenReturn(LocalDateTime.now().plusMinutes(15));
        QueryStateMachine machine = new QueryStateMachine(store, ignored -> { });
        agent = new ReadOnlyQueryAgent(
                model, properties, schemaRetriever, new SchemaPromptBuilder(), validator, executor,
                cipher, store, machine, sink, ignored -> { });
    }

    @Test
    void shouldExecuteBoundedToolLoopAndUseOnlySuccessfulSqlResult() {
        script(
                intent("FETCH"),
                tool("search_schema", null),
                tool("execute_readonly_sql", "SELECT COUNT(*) AS total FROM orders"),
                answer());
        when(executor.execute(any(), any(), any(Integer.class), any(Long.class)))
                .thenReturn(new QueryExecutionResult(List.of("total"), List.of(Map.of("total", 3))));

        QueryResultView result = agent.execute(task, datasource, schema, Instant.now());

        assertThat(result.sql()).isEqualTo("SELECT COUNT(*) AS total FROM orders LIMIT 100");
        assertThat(result.rows()).containsExactly(Map.of("total", 3));
        assertThat(task.getStatus()).isEqualTo("SUCCEEDED");
        verify(sink).store(result);
        ArgumentCaptor<AgentStepEntity> steps = ArgumentCaptor.forClass(AgentStepEntity.class);
        verify(store, org.mockito.Mockito.atLeast(4)).insertAgentStep(steps.capture());
        assertThat(steps.getAllValues()).allSatisfy(step -> {
            assertThat(step.getSummary()).doesNotContain("total=3", "secret");
            assertThat(step.getKind()).isIn("INTENT", "TOOL", "FINAL");
        });
    }

    @Test
    void shouldPersistClarificationAsTerminalWithoutExecutingSql() {
        script(new AgentDecision(
                "INTENT", "AMBIGUOUS", null, null, null, List.of(), null,
                "CLARIFY", "缺少时间范围", List.of(), null, null, "要查询哪个时间范围？"));

        QueryResultView result = agent.execute(task, datasource, schema, Instant.now());

        assertThat(result).isNull();
        assertThat(task.getStatus()).isEqualTo("NEEDS_CLARIFICATION");
        assertThat(task.getClarificationQuestion()).isEqualTo("要查询哪个时间范围？");
    }

    @Test
    void shouldRepairAfterClassifiedExecutionFailure() {
        script(
                intent("FETCH"),
                tool("search_schema", null),
                tool("execute_readonly_sql", "SELECT missing FROM orders"),
                tool("execute_readonly_sql", "SELECT COUNT(*) AS total FROM orders"),
                answer());
        when(executor.execute(any(), any(), any(Integer.class), any(Long.class)))
                .thenThrow(new AppException(
                        ResponseCode.READ_ONLY_QUERY_EXECUTION_FAILED, "UNKNOWN_COLUMN"))
                .thenReturn(new QueryExecutionResult(List.of("total"), List.of(Map.of("total", 3))));

        QueryResultView result = agent.execute(task, datasource, schema, Instant.now());

        assertThat(result.rowCount()).isEqualTo(1);
        assertThat(task.getRepairCount()).isEqualTo(1);
        assertThat(task.getStatus()).isEqualTo("SUCCEEDED");
    }

    @Test
    void shouldStopAfterThirdIdenticalToolFailure() {
        script(
                intent("FETCH"),
                tool("execute_readonly_sql", "SELECT 1"),
                tool("execute_readonly_sql", "SELECT 1"),
                tool("execute_readonly_sql", "SELECT 1"));

        assertThatThrownBy(() -> agent.execute(task, datasource, schema, Instant.now()))
                .isInstanceOfSatisfying(AppException.class,
                        exception -> assertThat(exception.getResponseCode())
                                .isEqualTo(ResponseCode.READ_ONLY_QUERY_EXECUTION_FAILED));
        assertThat(task.getStatus()).isEqualTo("FAILED");
        assertThat(task.getErrorCode()).isEqualTo("SCHEMA_NOT_PREPARED");
    }

    private void script(AgentDecision... decisions) {
        Queue<AgentDecision> queue = new ArrayDeque<>(List.of(decisions));
        when(model.next(any())).thenAnswer(invocation ->
                new AgentTurnOutcome(queue.remove(), metrics()));
    }

    private AgentDecision intent(String intent) {
        return new AgentDecision(
                "INTENT", intent, null, null, null, List.of(), null,
                null, "识别查询意图", List.of(), null, null, null);
    }

    private AgentDecision tool(String name, String sql) {
        return new AgentDecision(
                "TOOL_CALL", null, name, task.getQuestion(), 6, List.of("orders"), sql,
                null, null, List.of(), null, null, null);
    }

    private AgentDecision answer() {
        return new AgentDecision(
                "FINAL", null, null, null, null, List.of(), null,
                "ANSWER", "统计订单数量", List.of("orders"), "返回订单总数",
                new BigDecimal("0.95"), null);
    }

    private AiCallMetrics metrics() {
        return new AiCallMetrics("test-model", "data-agent-v1", 10, 5, 15, 2L);
    }
}
