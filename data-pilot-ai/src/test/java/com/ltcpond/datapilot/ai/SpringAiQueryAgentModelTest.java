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

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpringAiQueryAgentModelTest {

    @Test
    void shouldForceSingleStrictRoutingFunction() {
        ChatModel chatModel = modelReturningTool("accept_query", "{}");
        SpringAiQueryAgentModel model = new SpringAiQueryAgentModel(enabled(), chatModel);

        AgentTurnOutcome outcome = model.next(new AgentTurnRequest("查询订单", 1, null, List.of()));

        assertThat(outcome.action()).isInstanceOf(AgentAction.AcceptQuery.class);
        assertThat(outcome.metrics().promptVersion()).isEqualTo("data-agent-v4");
        ArgumentCaptor<Prompt> prompt = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(prompt.capture());
        assertThat(prompt.getValue().getContents())
                .contains("accept_query", "request_clarification", "reject_unsupported");
        OpenAiChatOptions options = (OpenAiChatOptions) prompt.getValue().getOptions();
        assertThat(options.getTimeout()).isEqualTo(SpringAiQueryAgentModel.REQUEST_TIMEOUT);
        assertThat(options.getToolChoice()).isEqualTo("required");
        assertThat(options.getParallelToolCalls()).isFalse();
        assertThat(options.getStrict()).isTrue();
        assertThat(options.getToolCallbacks()).extracting(callback -> callback.getToolDefinition().name())
                .containsExactly("accept_query", "request_clarification", "reject_unsupported");
        assertThat(options.getToolCallbacks()).allSatisfy(callback ->
                assertThat(callback.getToolDefinition().inputSchema())
                        .contains("additionalProperties"));
    }

    @Test
    void shouldParseTypedRunningToolArguments() {
        ChatModel chatModel = modelReturningTool(
                "search_schema", "{\"retrievalQuery\":\"订单与退款关联\",\"topK\":6}");
        SpringAiQueryAgentModel model = new SpringAiQueryAgentModel(enabled(), chatModel);

        AgentTurnOutcome outcome = model.next(new AgentTurnRequest("查询退款订单", 2, "QUERY", List.of()));

        assertThat(outcome.action()).isEqualTo(new AgentAction.SearchSchema("订单与退款关联", 6));
        ArgumentCaptor<Prompt> prompt = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(prompt.capture());
        OpenAiChatOptions options = (OpenAiChatOptions) prompt.getValue().getOptions();
        assertThat(options.getToolCallbacks()).extracting(callback -> callback.getToolDefinition().name())
                .containsExactly(
                        "search_schema", "get_schema", "execute_readonly_sql",
                        "finish_answer", "request_clarification", "reject_unsupported");
    }

    @Test
    void shouldParseConstrainedFinalAction() {
        ChatModel chatModel = modelReturningTool(
                "finish_answer", """
                        {"questionAnalysis":"统计订单数量","relatedTables":["orders"],
                         "explanation":"返回订单总数","confidence":0.95}
                        """);
        SpringAiQueryAgentModel model = new SpringAiQueryAgentModel(enabled(), chatModel);

        AgentTurnOutcome outcome = model.next(new AgentTurnRequest("查询订单", 4, "QUERY", List.of()));

        assertThat(outcome.action()).isEqualTo(new AgentAction.FinishAnswer(
                "统计订单数量", List.of("orders"), "返回订单总数", new BigDecimal("0.95")));
    }

    @Test
    void shouldReturnStableCodeWhenModelDoesNotCallAFunction() {
        SpringAiQueryAgentModel model = new SpringAiQueryAgentModel(
                enabled(), modelReturningMessage("not-json-secret"));

        assertThatThrownBy(() -> model.next(new AgentTurnRequest("查询订单", 1, null, List.of())))
                .isInstanceOfSatisfying(AppException.class,
                        exception -> assertThat(exception.getDetailCode())
                                .isEqualTo("AI_TOOL_CALLING_UNSUPPORTED"))
                .hasMessageNotContaining("not-json-secret");
    }

    @Test
    void shouldRejectFunctionThatIsUnavailableInCurrentPhase() {
        SpringAiQueryAgentModel model = new SpringAiQueryAgentModel(
                enabled(), modelReturningTool("execute_readonly_sql", "{\"sql\":\"SELECT 1\"}"));

        assertThatThrownBy(() -> model.next(new AgentTurnRequest("查询订单", 1, null, List.of())))
                .isInstanceOfSatisfying(AppException.class,
                        exception -> assertThat(exception.getDetailCode())
                                .isEqualTo("AI_TOOL_CALLING_UNSUPPORTED"));
    }

    private ChatModel modelReturningTool(String name, String arguments) {
        AssistantMessage output = AssistantMessage.builder()
                .content("")
                .toolCalls(List.of(new AssistantMessage.ToolCall("call-1", "function", name, arguments)))
                .build();
        return modelReturning(output, "tool_calls");
    }

    private ChatModel modelReturningMessage(String content) {
        return modelReturning(new AssistantMessage(content), "stop");
    }

    private ChatModel modelReturning(AssistantMessage output, String finishReason) {
        ChatModel chatModel = mock(ChatModel.class);
        Generation generation = new Generation(
                output, ChatGenerationMetadata.builder().finishReason(finishReason).build());
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
