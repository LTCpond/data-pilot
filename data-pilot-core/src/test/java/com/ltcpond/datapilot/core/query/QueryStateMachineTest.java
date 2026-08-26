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

        machine.transition(task, QueryStatus.SCHEMA_PREPARING);

        verify(store).updateTask(task);
        ArgumentCaptor<QueryStatusEvent> event = ArgumentCaptor.forClass(QueryStatusEvent.class);
        verify(publisher).publish(event.capture());
        assertThat(event.getValue().queryId()).isEqualTo(8L);
        assertThat(event.getValue().status()).isEqualTo("SCHEMA_PREPARING");
        assertThat(event.getValue().resultAvailable()).isFalse();
    }

    @Test
    void shouldReachCancelledTerminalState() {
        QueryStateMachine machine = new QueryStateMachine(
                mock(QueryTaskStore.class), mock(QueryEventPublisher.class));
        QueryTaskEntity task = task("SQL_EXECUTING");

        machine.transition(task, QueryStatus.CANCEL_REQUESTED);
        machine.transition(task, QueryStatus.CANCELLED);

        assertThat(task.getStatus()).isEqualTo("CANCELLED");
        assertThat(task.getCompletedAt()).isNotNull();
    }

    private QueryTaskEntity task(String status) {
        QueryTaskEntity task = new QueryTaskEntity();
        task.setId(8L);
        task.setStatus(status);
        return task;
    }
}
