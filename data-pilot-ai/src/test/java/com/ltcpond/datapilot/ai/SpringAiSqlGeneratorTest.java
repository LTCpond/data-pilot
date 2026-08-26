package com.ltcpond.datapilot.ai;

import com.ltcpond.datapilot.common.api.ResponseCode;
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
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;

import java.util.List;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpringAiSqlGeneratorTest {

    @Test
    void shouldRequestJsonAndCollectUsageMetrics() {
        ChatModel chatModel = modelReturning(response(validJson(), "stop"));
        SpringAiSqlGenerator generator = new SpringAiSqlGenerator(enabledProperties(), chatModel);

        SqlGenerationOutcome outcome = generator.generate(new SqlGenerationRequest(
                "查询订单数量", "表：orders（订单表）"));

        assertThat(outcome.result().answerable()).isTrue();
        assertThat(outcome.result().relatedTables()).containsExactly("orders");
        assertThat(outcome.metrics().model()).isEqualTo("deepseek-v4-pro");
        assertThat(outcome.metrics().promptVersion()).isEqualTo("text-to-sql-v2");
        assertThat(outcome.metrics().promptTokens()).isEqualTo(100);
        assertThat(outcome.metrics().completionTokens()).isEqualTo(50);
        assertThat(outcome.metrics().totalTokens()).isEqualTo(150);

        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(promptCaptor.capture());
        Prompt prompt = promptCaptor.getValue();
        assertThat(prompt.getContents())
                .contains("查询订单数量", "表：orders（订单表）", "text-to-sql-v2", "JSON 示例",
                        "不要额外返回仅用于 JOIN、GROUP BY 或 ORDER BY 的 ID", "默认使用 orders.created_at");
        OpenAiChatOptions options = (OpenAiChatOptions) prompt.getOptions();
        assertThat(options.getMaxCompletionTokens()).isEqualTo(1200);
        assertThat(options.getTimeout()).isEqualTo(SpringAiSqlGenerator.REQUEST_TIMEOUT);
        assertThat(options.getResponseFormat().getType())
                .isEqualTo(OpenAiChatModel.ResponseFormat.Type.JSON_OBJECT);
    }

    @Test
    void shouldRejectBlankResponseWithStableCode() {
        SpringAiSqlGenerator generator = new SpringAiSqlGenerator(
                enabledProperties(), modelReturning(response("   ", "stop")));

        assertErrorCode(generator, "AI_EMPTY_RESPONSE");
    }

    @Test
    void shouldRejectTruncatedResponseWithStableCode() {
        SpringAiSqlGenerator generator = new SpringAiSqlGenerator(
                enabledProperties(), modelReturning(response(validJson(), "length")));

        assertErrorCode(generator, "AI_RESPONSE_TRUNCATED");
    }

    @Test
    void shouldRejectInvalidJsonWithoutExposingContent() {
        SpringAiSqlGenerator generator = new SpringAiSqlGenerator(
                enabledProperties(), modelReturning(response("not-json-api-key-secret", "stop")));

        assertThatThrownBy(() -> generator.generate(new SqlGenerationRequest("问题", "Schema")))
                .isInstanceOfSatisfying(AppException.class, exception -> {
                    assertThat(exception.getResponseCode()).isEqualTo(ResponseCode.AI_SQL_GENERATION_FAILED);
                    assertThat(exception.getDetailCode()).isEqualTo("AI_RESPONSE_INVALID_JSON");
                    assertThat(exception.getMessage()).isEqualTo("AI SQL 生成失败");
                    assertThat(exception.getMessage()).doesNotContain("not-json", "api-key-secret");
                });
    }

    @Test
    void shouldMapTimeoutWithoutExposingCause() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.getOptions()).thenReturn(OpenAiChatOptions.builder().build());
        when(chatModel.call(any(Prompt.class)))
                .thenThrow(new RuntimeException("secret-key", new TimeoutException("timed out")));
        SpringAiSqlGenerator generator = new SpringAiSqlGenerator(enabledProperties(), chatModel);

        assertThatThrownBy(() -> generator.generate(new SqlGenerationRequest("问题", "Schema")))
                .isInstanceOfSatisfying(AppException.class, exception -> {
                    assertThat(exception.getDetailCode()).isEqualTo("AI_REQUEST_TIMEOUT");
                    assertThat(exception.getMessage()).doesNotContain("secret-key", "timed out");
                });
    }

    @Test
    void shouldStayUnavailableWhenAiIsDisabled() {
        DataPilotAiProperties properties = new DataPilotAiProperties();
        properties.setEnabled(false);
        SpringAiSqlGenerator generator = new SpringAiSqlGenerator(properties, mock(ChatModel.class));

        assertThatThrownBy(() -> generator.generate(new SqlGenerationRequest("问题", "Schema")))
                .isInstanceOfSatisfying(AppException.class, exception -> {
                    assertThat(exception.getResponseCode()).isEqualTo(ResponseCode.AI_MODEL_UNAVAILABLE);
                    assertThat(exception).hasMessage("AI 模型不可用");
                });
    }

    private void assertErrorCode(SpringAiSqlGenerator generator, String expected) {
        assertThatThrownBy(() -> generator.generate(new SqlGenerationRequest("问题", "Schema")))
                .isInstanceOfSatisfying(AppException.class,
                        exception -> assertThat(exception.getDetailCode()).isEqualTo(expected));
    }

    private ChatModel modelReturning(ChatResponse response) {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.getOptions()).thenReturn(OpenAiChatOptions.builder().build());
        when(chatModel.call(any(Prompt.class))).thenReturn(response);
        return chatModel;
    }

    private ChatResponse response(String content, String finishReason) {
        Generation generation = new Generation(
                new AssistantMessage(content),
                ChatGenerationMetadata.builder().finishReason(finishReason).build());
        ChatResponseMetadata metadata = ChatResponseMetadata.builder()
                .model("deepseek-v4-pro")
                .usage(new DefaultUsage(100, 50, 150))
                .build();
        return new ChatResponse(List.of(generation), metadata);
    }

    private DataPilotAiProperties enabledProperties() {
        DataPilotAiProperties properties = new DataPilotAiProperties();
        properties.setEnabled(true);
        return properties;
    }

    private String validJson() {
        return """
                {
                  "answerable": true,
                  "questionAnalysis": "统计订单数量",
                  "relatedTables": ["orders"],
                  "sql": "SELECT COUNT(*) AS order_count FROM orders",
                  "explanation": "统计订单表总行数",
                  "confidence": 0.95
                }
                """;
    }
}
