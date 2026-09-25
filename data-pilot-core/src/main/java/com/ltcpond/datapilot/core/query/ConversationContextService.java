package com.ltcpond.datapilot.core.query;

import com.ltcpond.datapilot.ai.ConversationTurn;
import com.ltcpond.datapilot.datasource.entity.QueryTaskEntity;
import com.ltcpond.datapilot.datasource.store.QueryTaskStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/** 为当前任务读取最近三轮会话历史，仅供首轮消歧。 */
@Component
@RequiredArgsConstructor
public class ConversationContextService {

    private static final int MAX_HISTORY_ROUNDS = 3;

    private final QueryTaskStore taskStore;

    public List<ConversationTurn> load(QueryTaskEntity currentTask) {
        if (currentTask.getConversationId() == null || currentTask.getId() == null) {
            return List.of();
        }
        return taskStore.findRecentConversationTasks(
                        currentTask.getConversationId(), currentTask.getDatasourceId(),
                        currentTask.getId(), MAX_HISTORY_ROUNDS)
                .stream()
                .map(task -> new ConversationTurn(
                        task.getQuestion(), task.getResolvedQuestion(),
                        task.getStatus(), task.getClarificationQuestion()))
                .toList();
    }
}
