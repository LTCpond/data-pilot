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

    /** 校验并持久化任务状态转换，同时向事件发布器广播最新状态。 */
    public void transition(QueryTaskEntity task, QueryStatus target) {
        QueryStatus current = QueryStatus.valueOf(task.getStatus());
        if (!TRANSITIONS.getOrDefault(current, Set.of()).contains(target)) {
            throw new IllegalStateException("查询状态转换无效");
        }
        LocalDateTime now = LocalDateTime.now();
        task.setStatus(target.name());
        task.setUpdatedAt(now);
        if (target == QueryStatus.SUCCEEDED
                || target == QueryStatus.FAILED
                || target == QueryStatus.CANCELLED
                || target == QueryStatus.NEEDS_CLARIFICATION) {
            task.setCompletedAt(now);
        }
        taskStore.updateTask(task);
        eventPublisher.publish(new QueryStatusEvent(
                task.getId(), task.getStatus(), task.getErrorCode(),
                target == QueryStatus.SUCCEEDED, now));
    }

    private static Map<QueryStatus, Set<QueryStatus>> transitions() {
        Map<QueryStatus, Set<QueryStatus>> transitions = new EnumMap<>(QueryStatus.class);
        transitions.put(QueryStatus.CREATED, EnumSet.of(QueryStatus.AGENT_ROUTING, QueryStatus.FAILED));
        transitions.put(QueryStatus.AGENT_ROUTING,
                EnumSet.of(QueryStatus.AGENT_RUNNING, QueryStatus.NEEDS_CLARIFICATION, QueryStatus.FAILED));
        transitions.put(QueryStatus.AGENT_RUNNING,
                EnumSet.of(QueryStatus.AGENT_FINALIZING, QueryStatus.NEEDS_CLARIFICATION, QueryStatus.FAILED));
        transitions.put(QueryStatus.AGENT_FINALIZING,
                EnumSet.of(QueryStatus.SUCCEEDED, QueryStatus.FAILED));
        for (QueryStatus active : List.of(
                QueryStatus.CREATED,
                QueryStatus.AGENT_ROUTING,
                QueryStatus.AGENT_RUNNING,
                QueryStatus.AGENT_FINALIZING)) {
            transitions.get(active).add(QueryStatus.CANCEL_REQUESTED);
        }
        transitions.put(QueryStatus.CANCEL_REQUESTED,
                EnumSet.of(QueryStatus.CANCELLED, QueryStatus.FAILED));
        return Map.copyOf(transitions);
    }
}
