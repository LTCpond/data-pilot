package com.ltcpond.datapilot.core.query;

import com.ltcpond.datapilot.datasource.entity.QueryTaskEntity;
import com.ltcpond.datapilot.datasource.store.QueryTaskStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 校验状态转换并立即持久化，避免流程分支产生不可能的任务状态。 */
@Component
public class QueryStateMachine {

    private static final Map<QueryStatus, Set<QueryStatus>> TRANSITIONS = transitions();

    private final QueryTaskStore taskStore;
    private final QueryEventPublisher eventPublisher;

    @Autowired
    public QueryStateMachine(
            QueryTaskStore taskStore,
            ObjectProvider<QueryEventPublisher> publisherProvider) {
        this(taskStore, publisherProvider.getIfAvailable(() -> ignored -> { }));
    }

    QueryStateMachine(QueryTaskStore taskStore) {
        this(taskStore, ignored -> { });
    }

    QueryStateMachine(QueryTaskStore taskStore, QueryEventPublisher eventPublisher) {
        this.taskStore = taskStore;
        this.eventPublisher = eventPublisher;
    }

    public void transition(QueryTaskEntity task, QueryStatus target) {
        QueryStatus current = QueryStatus.valueOf(task.getStatus());
        if (!TRANSITIONS.getOrDefault(current, Set.of()).contains(target)) {
            throw new IllegalStateException("Invalid query state transition");
        }
        LocalDateTime now = LocalDateTime.now();
        task.setStatus(target.name());
        task.setUpdatedAt(now);
        if (target == QueryStatus.SUCCEEDED
                || target == QueryStatus.FAILED
                || target == QueryStatus.CANCELLED) {
            task.setCompletedAt(now);
        }
        taskStore.updateTask(task);
        eventPublisher.publish(new QueryStatusEvent(
                task.getId(), task.getStatus(), task.getErrorCode(),
                target == QueryStatus.SUCCEEDED, now));
    }

    private static Map<QueryStatus, Set<QueryStatus>> transitions() {
        Map<QueryStatus, Set<QueryStatus>> transitions = new EnumMap<>(QueryStatus.class);
        transitions.put(QueryStatus.CREATED, EnumSet.of(QueryStatus.SCHEMA_PREPARING, QueryStatus.FAILED));
        transitions.put(QueryStatus.SCHEMA_PREPARING, EnumSet.of(QueryStatus.SQL_GENERATING, QueryStatus.FAILED));
        transitions.put(QueryStatus.SQL_GENERATING, EnumSet.of(QueryStatus.SQL_VALIDATING, QueryStatus.FAILED));
        transitions.put(QueryStatus.SQL_VALIDATING,
                EnumSet.of(QueryStatus.SQL_EXECUTING, QueryStatus.SQL_REPAIRING, QueryStatus.FAILED));
        transitions.put(QueryStatus.SQL_REPAIRING, EnumSet.of(QueryStatus.SQL_VALIDATING, QueryStatus.FAILED));
        transitions.put(QueryStatus.SQL_EXECUTING,
                EnumSet.of(QueryStatus.SQL_REPAIRING, QueryStatus.SUCCEEDED, QueryStatus.FAILED));
        for (QueryStatus active : List.of(
                QueryStatus.CREATED,
                QueryStatus.SCHEMA_PREPARING,
                QueryStatus.SQL_GENERATING,
                QueryStatus.SQL_VALIDATING,
                QueryStatus.SQL_REPAIRING,
                QueryStatus.SQL_EXECUTING)) {
            transitions.get(active).add(QueryStatus.CANCEL_REQUESTED);
        }
        transitions.put(QueryStatus.CANCEL_REQUESTED,
                EnumSet.of(QueryStatus.CANCELLED, QueryStatus.FAILED));
        return Map.copyOf(transitions);
    }
}
