package com.ltcpond.datapilot.core.query;

import com.ltcpond.datapilot.datasource.entity.QueryTaskEntity;
import com.ltcpond.datapilot.datasource.store.QueryTaskStore;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class QueryStateMachineTest {

    @Test
    void shouldPersistAndPublishEveryTransition() {
        QueryTaskStore store = mock(QueryTaskStore.class);
        QueryEventPublisher publisher = mock(QueryEventPublisher.class);
        QueryStateMachine machine = new QueryStateMachine(store, publisher);
        QueryTaskEntity task = task("CREATED");

        machine.transition(task, QueryStatus.AGENT_ROUTING);

        verify(store).updateTask(task);
        ArgumentCaptor<QueryStatusEvent> event = ArgumentCaptor.forClass(QueryStatusEvent.class);
        verify(publisher).publish(event.capture());
        assertThat(event.getValue().queryId()).isEqualTo(8L);
        assertThat(event.getValue().status()).isEqualTo("AGENT_ROUTING");
        assertThat(event.getValue().resultAvailable()).isFalse();
    }

    @Test
    void shouldReachCancelledTerminalState() {
        QueryStateMachine machine = new QueryStateMachine(
                mock(QueryTaskStore.class), mock(QueryEventPublisher.class));
        QueryTaskEntity task = task("AGENT_RUNNING");

        machine.transition(task, QueryStatus.CANCEL_REQUESTED);
        machine.transition(task, QueryStatus.CANCELLED);

        assertThat(task.getStatus()).isEqualTo("CANCELLED");
        assertThat(task.getCompletedAt()).isNotNull();
    }

    @Test
    void shouldTreatClarificationAsCompletedTerminalState() {
        QueryStateMachine machine = new QueryStateMachine(
                mock(QueryTaskStore.class), mock(QueryEventPublisher.class));
        QueryTaskEntity task = task("AGENT_ROUTING");

        machine.transition(task, QueryStatus.NEEDS_CLARIFICATION);

        assertThat(task.getCompletedAt()).isNotNull();
        assertThat(task.getStatus()).isEqualTo("NEEDS_CLARIFICATION");
    }

    private QueryTaskEntity task(String status) {
        QueryTaskEntity task = new QueryTaskEntity();
        task.setId(8L);
        task.setStatus(status);
        return task;
    }
}
