package com.ltcpond.datapilot.core.query;

import com.ltcpond.datapilot.ai.AiCallMetrics;
import com.ltcpond.datapilot.ai.DataPilotAiProperties;
import com.ltcpond.datapilot.ai.SqlGenerationRequest;
import com.ltcpond.datapilot.ai.SqlGenerationResult;
import com.ltcpond.datapilot.ai.SqlGenerationOutcome;
import com.ltcpond.datapilot.ai.SqlGenerator;
import com.ltcpond.datapilot.ai.rag.RagProperties;
import com.ltcpond.datapilot.ai.rag.SchemaVectorIndex;
import com.ltcpond.datapilot.common.api.ResponseCode;
import com.ltcpond.datapilot.common.exception.AppException;
import com.ltcpond.datapilot.core.datasource.DatasourceSchemaView;
import com.ltcpond.datapilot.core.datasource.DatasourceService;
import com.ltcpond.datapilot.core.datasource.SchemaColumnView;
import com.ltcpond.datapilot.core.datasource.SchemaTableView;
import com.ltcpond.datapilot.datasource.crypto.CredentialCipher;
import com.ltcpond.datapilot.datasource.entity.DatasourceEntity;
import com.ltcpond.datapilot.datasource.entity.QueryAttemptEntity;
import com.ltcpond.datapilot.datasource.entity.QueryTaskEntity;
import com.ltcpond.datapilot.datasource.query.QueryExecutionResult;
import com.ltcpond.datapilot.datasource.query.ReadOnlyQueryExecutor;
import com.ltcpond.datapilot.datasource.store.DatasourceStore;
import com.ltcpond.datapilot.datasource.store.QueryTaskStore;
import com.ltcpond.datapilot.sql.SqlValidationResult;
import com.ltcpond.datapilot.sql.SqlValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QueryServiceTest {

    private DatasourceService datasourceService;
    private DatasourceStore datasourceStore;
    private QueryTaskStore taskStore;
    private CredentialCipher credentialCipher;
    private SqlGenerator sqlGenerator;
    private SqlValidator sqlValidator;
    private ReadOnlyQueryExecutor queryExecutor;
    private QueryService service;
    private AtomicReference<QueryTaskEntity> storedTask;

    @BeforeEach
    void setUp() {
        datasourceService = mock(DatasourceService.class);
        datasourceStore = mock(DatasourceStore.class);
        taskStore = mock(QueryTaskStore.class);
        credentialCipher = mock(CredentialCipher.class);
        sqlGenerator = mock(SqlGenerator.class);
        sqlValidator = mock(SqlValidator.class);
        queryExecutor = mock(ReadOnlyQueryExecutor.class);
        storedTask = new AtomicReference<>();

        DataPilotAiProperties properties = new DataPilotAiProperties();
        properties.setEnabled(true);
        properties.setMaximumRepairAttempts(2);
        properties.setDefaultMaxRows(100);
        properties.setAbsoluteMaxRows(200);
        RagProperties ragProperties = new RagProperties();

        service = new QueryService(
                datasourceService,
                datasourceStore,
                taskStore,
                credentialCipher,
                sqlGenerator,
                sqlValidator,
                queryExecutor,
                new SchemaPromptBuilder(),
                new SchemaRetriever(mock(SchemaVectorIndex.class), ragProperties),
                new QueryStateMachine(taskStore),
                properties);

        when(datasourceStore.findById(1L)).thenReturn(Optional.of(datasource()));
        when(datasourceService.schema(1L)).thenReturn(schema());
        when(taskStore.insertTask(any())).thenAnswer(invocation -> {
            QueryTaskEntity task = invocation.getArgument(0);
            task.setId(11L);
            storedTask.set(task);
            return task;
        });
        when(taskStore.findTask(11L)).thenAnswer(ignored -> Optional.ofNullable(storedTask.get()));
        when(credentialCipher.decrypt("v1:ciphertext")).thenReturn("database-secret");
    }

    @Test
    void shouldGenerateValidateAndExecuteReadOnlyQuery() {
        when(sqlGenerator.generate(any())).thenReturn(generation("SELECT COUNT(*) AS order_count FROM orders"));
        when(sqlValidator.validate(any())).thenReturn(
                SqlValidationResult.accepted("SELECT COUNT(*) AS order_count FROM orders LIMIT 100"));
        when(queryExecutor.execute(any(), any(), any(Integer.class), anyLong())).thenReturn(new QueryExecutionResult(
                List.of("order_count"), List.of(Map.of("order_count", 60L))));

        QueryResultView result = service.execute(new QueryCommand(1L, "查询订单数量", null));

        assertThat(result.status()).isEqualTo("SUCCEEDED");
        assertThat(result.rowCount()).isEqualTo(1);
        assertThat(result.rows().getFirst()).containsEntry("order_count", 60L);
        ArgumentCaptor<SqlGenerationRequest> requestCaptor = ArgumentCaptor.forClass(SqlGenerationRequest.class);
        verify(sqlGenerator).generate(requestCaptor.capture());
        assertThat(requestCaptor.getValue().schema())
                .contains("orders", "订单表", "订单ID")
                .doesNotContain("database-secret", "v1:ciphertext", "jdbc:mysql");
        ArgumentCaptor<QueryAttemptEntity> attemptCaptor = ArgumentCaptor.forClass(QueryAttemptEntity.class);
        verify(taskStore).insertAttempt(attemptCaptor.capture());
        assertThat(attemptCaptor.getValue().getModelName()).isEqualTo("deepseek-v4-pro");
        assertThat(attemptCaptor.getValue().getPromptVersion()).isEqualTo("text-to-sql-v2");
        assertThat(attemptCaptor.getValue().getPromptTokens()).isEqualTo(100);
        assertThat(attemptCaptor.getValue().getCompletionTokens()).isEqualTo(50);
        assertThat(attemptCaptor.getValue().getTotalTokens()).isEqualTo(150);
        assertThat(attemptCaptor.getValue().getModelDurationMs()).isEqualTo(20);
    }

    @Test
    void shouldRepairOnceAfterValidationFailure() {
        when(sqlGenerator.generate(any())).thenReturn(generation("SELECT missing FROM orders"));
        when(sqlGenerator.repair(any())).thenReturn(generation("SELECT id FROM orders"));
        when(sqlValidator.validate(any()))
                .thenReturn(SqlValidationResult.rejected(List.of("SQL_PARSE_ERROR")))
                .thenReturn(SqlValidationResult.accepted("SELECT id FROM orders LIMIT 100"));
        when(queryExecutor.execute(any(), any(), any(Integer.class), anyLong())).thenReturn(new QueryExecutionResult(
                List.of("id"), List.of(Map.of("id", 1L))));

        QueryResultView result = service.execute(new QueryCommand(1L, "查询订单", 100));

        assertThat(result.status()).isEqualTo("SUCCEEDED");
        verify(sqlGenerator).repair(any());
        verify(taskStore, times(2)).insertAttempt(any(QueryAttemptEntity.class));
    }

    @Test
    void shouldStopAfterTwoRepairsAndNeverExecuteRejectedSql() {
        when(sqlGenerator.generate(any())).thenReturn(generation("DELETE FROM orders"));
        when(sqlGenerator.repair(any())).thenReturn(generation("DELETE FROM orders"));
        when(sqlValidator.validate(any())).thenReturn(
                SqlValidationResult.rejected(List.of("NON_SELECT_STATEMENT")));

        assertThatThrownBy(() -> service.execute(new QueryCommand(1L, "删除所有订单", 100)))
                .isInstanceOfSatisfying(AppException.class, exception ->
                        assertThat(exception.getResponseCode()).isEqualTo(ResponseCode.QUERY_REJECTED));

        verify(sqlGenerator, times(2)).repair(any());
        verify(queryExecutor, never()).execute(any(), any(), any(Integer.class), anyLong());
        ArgumentCaptor<QueryTaskEntity> taskCaptor = ArgumentCaptor.forClass(QueryTaskEntity.class);
        verify(taskStore, times(9)).updateTask(taskCaptor.capture());
        assertThat(taskCaptor.getAllValues().getLast().getStatus()).isEqualTo("FAILED");
    }

    @Test
    void shouldRejectQuestionMarkedAsUnanswerable() {
        when(sqlGenerator.generate(any())).thenReturn(outcome(new SqlGenerationResult(
                false, "问题要求修改数据", List.of(), "", "不允许写操作", new BigDecimal("0.99"))));

        assertThatThrownBy(() -> service.execute(new QueryCommand(1L, "删除所有订单", null)))
                .isInstanceOfSatisfying(AppException.class, exception ->
                        assertThat(exception.getResponseCode()).isEqualTo(ResponseCode.QUERY_REJECTED));
        verify(sqlValidator, never()).validate(any());
        verify(queryExecutor, never()).execute(any(), any(), any(Integer.class), anyLong());
    }

    @Test
    void shouldNotAskModelToRepairAConnectionFailure() {
        when(sqlGenerator.generate(any())).thenReturn(generation("SELECT id FROM orders"));
        when(sqlValidator.validate(any())).thenReturn(
                SqlValidationResult.accepted("SELECT id FROM orders LIMIT 100"));
        when(queryExecutor.execute(any(), any(), any(Integer.class), anyLong()))
                .thenThrow(new AppException(
                        ResponseCode.READ_ONLY_QUERY_EXECUTION_FAILED, "CONNECTION_FAILED"));

        assertThatThrownBy(() -> service.execute(new QueryCommand(1L, "查询订单", null)))
                .isInstanceOfSatisfying(AppException.class, exception ->
                        assertThat(exception.getResponseCode()).isEqualTo(
                                ResponseCode.READ_ONLY_QUERY_EXECUTION_FAILED));

        verify(sqlGenerator, never()).repair(any());
    }

    @Test
    void shouldCreateAsyncTaskWithoutCallingModel() {
        QueryTaskView task = service.createAsync(new QueryCommand(1L, "查询订单", 50));

        assertThat(task.executionMode()).isEqualTo("ASYNC");
        assertThat(task.status()).isEqualTo("CREATED");
        assertThat(storedTask.get().getMaxRows()).isEqualTo(50);
        verify(sqlGenerator, never()).generate(any());
    }

    @Test
    void shouldRequestCancellationAndCancelActiveJdbcStatement() {
        QueryTaskView created = service.createAsync(new QueryCommand(1L, "查询订单", 50));

        QueryTaskView cancelled = service.cancel(created.id());

        assertThat(cancelled.status()).isEqualTo("CANCEL_REQUESTED");
        verify(queryExecutor).cancel(created.id());
        assertThatThrownBy(() -> service.executeTask(created.id(), QueryResultSink.none()))
                .isInstanceOfSatisfying(AppException.class, exception ->
                        assertThat(exception.getResponseCode()).isEqualTo(ResponseCode.QUERY_TASK_CANCELLED));
        assertThat(storedTask.get().getStatus()).isEqualTo("CANCELLED");
        verify(sqlGenerator, never()).generate(any());
    }

    private DatasourceEntity datasource() {
        DatasourceEntity datasource = new DatasourceEntity();
        datasource.setId(1L);
        datasource.setStatus("READY");
        datasource.setJdbcUrl("jdbc:mysql://127.0.0.1:3307/ecommerce_demo");
        datasource.setUsername("reader");
        datasource.setEncryptedPassword("v1:ciphertext");
        return datasource;
    }

    private DatasourceSchemaView schema() {
        SchemaColumnView id = new SchemaColumnView(
                1L, "id", java.sql.Types.BIGINT, "BIGINT", 1, false, true, "订单ID");
        SchemaTableView orders = new SchemaTableView(
                1L, "ecommerce_demo", "orders", "TABLE", "订单表", List.of(id), List.of());
        return new DatasourceSchemaView(1L, List.of(orders));
    }

    private SqlGenerationOutcome generation(String sql) {
        return outcome(new SqlGenerationResult(
                true,
                "查询订单数据",
                List.of("orders"),
                sql,
                "从订单表查询",
                new BigDecimal("0.90")));
    }

    private SqlGenerationOutcome outcome(SqlGenerationResult result) {
        return new SqlGenerationOutcome(result, new AiCallMetrics(
                "deepseek-v4-pro", "text-to-sql-v2", 100, 50, 150, 20));
    }
}
