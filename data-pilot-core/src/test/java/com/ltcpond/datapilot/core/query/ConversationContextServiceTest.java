package com.ltcpond.datapilot.core.query;

import com.ltcpond.datapilot.ai.ConversationTurn;
import com.ltcpond.datapilot.datasource.entity.QueryTaskEntity;
import com.ltcpond.datapilot.datasource.store.QueryTaskStore;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConversationContextServiceTest {

    @Test
    void shouldLoadPriorClarificationAsConversationTurn() {
        QueryTaskStore store = mock(QueryTaskStore.class);
        QueryTaskEntity current = new QueryTaskEntity();
        current.setId(8L);
        current.setDatasourceId(1L);
        current.setConversationId("conversation-1");
        QueryTaskEntity prior = new QueryTaskEntity();
        prior.setQuestion("查一下销售情况");
        prior.setStatus("NEEDS_CLARIFICATION");
        prior.setClarificationQuestion("要查询哪个时间范围？");
        when(store.findRecentConversationTasks("conversation-1", 1L, 8L, 3))
                .thenReturn(List.of(prior));

        List<ConversationTurn> history = new ConversationContextService(store).load(current);

        assertThat(history).containsExactly(new ConversationTurn(
                "查一下销售情况", null, "NEEDS_CLARIFICATION", "要查询哪个时间范围？"));
        verify(store).findRecentConversationTasks("conversation-1", 1L, 8L, 3);
    }
}
