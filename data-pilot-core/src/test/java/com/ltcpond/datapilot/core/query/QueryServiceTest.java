package com.ltcpond.datapilot.core.query;

import com.ltcpond.datapilot.ai.DataPilotAiProperties;
import com.ltcpond.datapilot.core.datasource.DatasourceSchemaView;
import com.ltcpond.datapilot.core.datasource.DatasourceService;
import com.ltcpond.datapilot.core.datasource.SchemaTableView;
import com.ltcpond.datapilot.datasource.entity.AgentStepEntity;
import com.ltcpond.datapilot.datasource.entity.DatasourceEntity;
import com.ltcpond.datapilot.datasource.entity.QueryTaskEntity;
import com.ltcpond.datapilot.datasource.query.ReadOnlyQueryExecutor;
import com.ltcpond.datapilot.datasource.store.DatasourceStore;
import com.ltcpond.datapilot.datasource.store.QueryTaskStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QueryServiceTest {

    private DatasourceService datasourceService;
    private DatasourceStore datasourceStore;
    private QueryTaskStore taskStore;
    private ReadOnlyQueryExecutor executor;
    private QueryStateMachine stateMachine;
    private ReadOnlyQueryAgent agent;
    private QueryService service;
    private DatasourceEntity datasource;
    private DatasourceSchemaView schema;

    @BeforeEach
    void setUp() {
        datasourceService = mock(DatasourceService.class);
        datasourceStore = mock(DatasourceStore.class);
        taskStore = mock(QueryTaskStore.class);
        executor = mock(ReadOnlyQueryExecutor.class);
        stateMachine = mock(QueryStateMachine.class);
        agent = mock(ReadOnlyQueryAgent.class);
        DataPilotAiProperties properties = new DataPilotAiProperties();
        properties.setDefaultMaxRows(100);
        properties.setAbsoluteMaxRows(200);
        service = new QueryService(
                datasourceService, datasourceStore, taskStore, executor, stateMachine, agent, properties);

        datasource = new DatasourceEntity();
        datasource.setId(1L);
        datasource.setStatus("READY");
        schema = new DatasourceSchemaView(1L, List.of(
                new SchemaTableView(1L, "demo", "orders", "TABLE", "订单", List.of(), List.of())));
        when(datasourceStore.findById(1L)).thenReturn(Optional.of(datasource));
        when(datasourceService.schema(1L)).thenReturn(schema);
        when(taskStore.insertTask(any())).thenAnswer(invocation -> {
            QueryTaskEntity task = invocation.getArgument(0);
            task.setId(9L);
            return task;
        });
    }

    @Test
    void shouldCreateTaskWithoutRunningAgentAndClampRows() {
        QueryTaskView task = service.createTask(new QueryCommand(1L, " 查询订单 ", 999));

        assertThat(task.id()).isEqualTo(9L);
        assertThat(task.status()).isEqualTo("CREATED");
        verify(agent, never()).execute(any(), any(), any(), any());
    }

    @Test
    void shouldDelegateExecutionToReadOnlyAgent() {
        QueryTaskEntity task = task("CREATED");
        when(taskStore.findTask(9L)).thenReturn(Optional.of(task));
        QueryResultView expected = mock(QueryResultView.class);
        when(agent.execute(any(), any(), any(), any())).thenReturn(expected);

        assertThat(service.executeTask(9L)).isSameAs(expected);
        verify(agent).execute(eq(task), eq(datasource), eq(schema), any());
    }

    @Test
    void shouldCancelActiveAgentAndJdbcStatement() {
        QueryTaskEntity task = task("AGENT_RUNNING");
        when(taskStore.findTask(9L)).thenReturn(Optional.of(task));

        service.cancel(9L);

        verify(stateMachine).transition(task, QueryStatus.CANCEL_REQUESTED);
        verify(executor).cancel(9L);
    }

    @Test
    void shouldReturnPersistedAgentSteps() {
        QueryTaskEntity task = task("SUCCEEDED");
        AgentStepEntity step = new AgentStepEntity();
        step.setId(1L);
        step.setTaskId(9L);
        step.setStepNo(1);
        step.setKind("INTENT");
        step.setStatus("SUCCEEDED");
        step.setSummary("识别意图：FETCH");
        step.setStartedAt(LocalDateTime.now());
        step.setCompletedAt(LocalDateTime.now());
        when(taskStore.findTask(9L)).thenReturn(Optional.of(task));
        when(taskStore.findAgentSteps(9L)).thenReturn(List.of(step));

        assertThat(service.steps(9L)).singleElement()
                .extracting(AgentStepView::summary)
                .isEqualTo("识别意图：FETCH");
    }

    private QueryTaskEntity task(String status) {
        QueryTaskEntity task = new QueryTaskEntity();
        task.setId(9L);
        task.setDatasourceId(1L);
        task.setQuestion("查询订单");
        task.setMaxRows(100);
        task.setRepairCount(0);
        task.setStatus(status);
        task.setCreatedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        return task;
    }
}
