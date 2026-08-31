package com.ltcpond.datapilot.ai;

import com.ltcpond.datapilot.common.exception.AppException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpringAiQueryAgentModelTest {

    @Test
    void shouldParseStructuredDecisionAndExposeOnlyControlledTools() {
        ChatModel chatModel = modelReturning("""
                {"type":"INTENT","intent":"FETCH","tableNames":[],"relatedTables":[]}
                """);
        SpringAiQueryAgentModel model = new SpringAiQueryAgentModel(enabled(), chatModel);

        AgentTurnOutcome outcome = model.next(new AgentTurnRequest("查询订单", 1, null, List.of()));

        assertThat(outcome.decision().intent()).isEqualTo("FETCH");
        assertThat(outcome.metrics().promptVersion()).isEqualTo("data-agent-v1");
        ArgumentCaptor<Prompt> prompt = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(prompt.capture());
        assertThat(prompt.getValue().getContents())
                .contains("search_schema", "get_schema", "execute_readonly_sql")
                .doesNotContain("文件工具", "网络工具");
        assertThat(((OpenAiChatOptions) prompt.getValue().getOptions()).getTimeout())
                .isEqualTo(SpringAiQueryAgentModel.REQUEST_TIMEOUT);
    }

    @Test
    void shouldReturnStableCodeWhenModelCannotProduceToolProtocol() {
        SpringAiQueryAgentModel model = new SpringAiQueryAgentModel(enabled(), modelReturning("not-json-secret"));

        assertThatThrownBy(() -> model.next(new AgentTurnRequest("查询订单", 1, null, List.of())))
                .isInstanceOfSatisfying(AppException.class,
                        exception -> assertThat(exception.getDetailCode())
                                .isEqualTo("AI_TOOL_CALLING_UNSUPPORTED"))
                .hasMessageNotContaining("not-json-secret");
    }

    private ChatModel modelReturning(String content) {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.getOptions()).thenReturn(OpenAiChatOptions.builder().build());
        Generation generation = new Generation(
                new AssistantMessage(content),
                ChatGenerationMetadata.builder().finishReason("stop").build());
        ChatResponse response = new ChatResponse(
                List.of(generation),
                ChatResponseMetadata.builder()
                        .model("test-model")
                        .usage(new DefaultUsage(10, 5, 15))
                        .build());
        when(chatModel.call(any(Prompt.class))).thenReturn(response);
        return chatModel;
    }

    private DataPilotAiProperties enabled() {
        DataPilotAiProperties properties = new DataPilotAiProperties();
        properties.setEnabled(true);
        properties.setModel("test-model");
        return properties;
    }
}
