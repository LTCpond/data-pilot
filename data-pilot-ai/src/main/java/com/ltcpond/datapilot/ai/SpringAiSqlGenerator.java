package com.ltcpond.datapilot.ai;

import com.ltcpond.datapilot.common.api.ResponseCode;
import com.ltcpond.datapilot.common.exception.AppException;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.TimeoutException;

/** 使用 Spring AI ChatClient 生成类型安全的 SQL 候选。 */
final class SpringAiSqlGenerator implements SqlGenerator {

    static final String PROMPT_VERSION = "text-to-sql-v2";
    static final int MAX_COMPLETION_TOKENS = 1200;
    static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60);

    private static final String SYSTEM_PROMPT = """
            Prompt 版本：text-to-sql-v2。
            你是一个谨慎的 MySQL Text-to-SQL 生成器，必须只输出一个合法 JSON 对象。
            只能使用用户消息提供的表、字段和关联关系。
            只能生成一条只读 SELECT，允许 WITH ... SELECT。
            禁止生成 INSERT、UPDATE、DELETE、DDL、系统库查询、锁语句或文件操作。
            不要猜测不存在的表和字段，不要输出 Markdown 代码块。
            如果问题不能由给定 Schema 回答，将 answerable 设置为 false，并将 sql 设置为空字符串。
            relatedTables 只填写实际使用的表名；confidence 取 0 到 1。
            SELECT 只返回问题要求展示的字段，不要额外返回仅用于 JOIN、GROUP BY 或 ORDER BY 的 ID。
            用户、店铺、商品等业务维度默认返回可读名称；只有问题明确要求返回 ID 时才展示 ID。
            订单量、销售额和销售趋势的时间口径默认使用 orders.created_at；只有问题明确提到支付时间时才使用 paid_at。
            固定日期范围使用左闭右开的时间条件，避免遗漏结束日期当天的数据。

            JSON 示例：
            {"answerable":true,"questionAnalysis":"统计订单数量","relatedTables":["orders"],"sql":"SELECT COUNT(*) AS order_count FROM orders","explanation":"统计订单表总行数","confidence":0.95}
            """;

    private static final String GENERATION_PROMPT = """
            用户问题：
            {question}

            可用 Schema：
            {schema}

            请生成结构化结果。
            """;

    private static final String REPAIR_PROMPT = """
            用户问题：
            {question}

            可用 Schema：
            {schema}

            上一次候选 SQL：
            {previousSql}

            脱敏后的失败原因：
            {reason}

            请修正 SQL 并返回完整结构化结果，不要重复明显错误。
            """;

    private final DataPilotAiProperties properties;
    private final ChatClient chatClient;
    private final BeanOutputConverter<SqlGenerationResult> outputConverter =
            new BeanOutputConverter<>(SqlGenerationResult.class);

    SpringAiSqlGenerator(DataPilotAiProperties properties, ChatModel chatModel) {
        this.properties = properties;
        this.chatClient = chatModel == null ? null : ChatClient.create(chatModel);
    }

    @Override
    public SqlGenerationOutcome generate(SqlGenerationRequest request) {
        ensureAvailable();
        return call(GENERATION_PROMPT
                .replace("{question}", request.question())
                .replace("{schema}", request.schema()));
    }

    @Override
    public SqlGenerationOutcome repair(SqlRepairRequest request) {
        ensureAvailable();
        return call(REPAIR_PROMPT
                .replace("{question}", request.question())
                .replace("{schema}", request.schema())
                .replace("{previousSql}", request.previousSql())
                .replace("{reason}", request.sanitizedReason()));
    }

    private SqlGenerationOutcome call(String userPrompt) {
        long startedAt = System.nanoTime();
        try {
            ChatResponse response = chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .user(userPrompt)
                    .options(requestOptions())
                    .call()
                    .chatResponse();
            if (response == null || response.getResult() == null
                    || response.getResult().getOutput() == null) {
                throw new AppException(ResponseCode.AI_SQL_GENERATION_FAILED, "AI_EMPTY_RESPONSE");
            }
            String finishReason = response.getResult().getMetadata() == null
                    ? null
                    : response.getResult().getMetadata().getFinishReason();
            if (isTruncated(finishReason)) {
                throw new AppException(ResponseCode.AI_SQL_GENERATION_FAILED, "AI_RESPONSE_TRUNCATED");
            }
            String content = response.getResult().getOutput().getText();
            if (content == null || content.isBlank()) {
                throw new AppException(ResponseCode.AI_SQL_GENERATION_FAILED, "AI_EMPTY_RESPONSE");
            }
            SqlGenerationResult result;
            try {
                result = outputConverter.convert(content);
            } catch (RuntimeException exception) {
                throw new AppException(
                        ResponseCode.AI_SQL_GENERATION_FAILED, "AI_RESPONSE_INVALID_JSON", exception);
            }
            if (result == null) {
                throw new AppException(ResponseCode.AI_SQL_GENERATION_FAILED, "AI_RESPONSE_INVALID_JSON");
            }
            return new SqlGenerationOutcome(result, metrics(response, elapsedMillis(startedAt)));
        } catch (AppException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            String errorCode = hasTimeoutCause(exception) ? "AI_REQUEST_TIMEOUT" : "AI_GENERATION_FAILED";
            throw new AppException(ResponseCode.AI_SQL_GENERATION_FAILED, errorCode, exception);
        }
    }

    private OpenAiChatOptions.Builder requestOptions() {
        OpenAiChatModel.ResponseFormat responseFormat = OpenAiChatModel.ResponseFormat.builder()
                .type(OpenAiChatModel.ResponseFormat.Type.JSON_OBJECT)
                .build();
        return OpenAiChatOptions.builder()
                .responseFormat(responseFormat)
                .maxCompletionTokens(MAX_COMPLETION_TOKENS)
                .timeout(REQUEST_TIMEOUT);
    }

    private AiCallMetrics metrics(ChatResponse response, long durationMs) {
        ChatResponseMetadata metadata = response.getMetadata();
        Usage usage = metadata == null ? null : metadata.getUsage();
        return new AiCallMetrics(
                metadata == null || metadata.getModel() == null ? properties.getModel() : metadata.getModel(),
                PROMPT_VERSION,
                usage == null ? null : usage.getPromptTokens(),
                usage == null ? null : usage.getCompletionTokens(),
                usage == null ? null : usage.getTotalTokens(),
                durationMs);
    }

    private boolean isTruncated(String finishReason) {
        if (finishReason == null) {
            return false;
        }
        String normalized = finishReason.toLowerCase(Locale.ROOT);
        return normalized.equals("length") || normalized.contains("max_tokens");
    }

    private boolean hasTimeoutCause(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof TimeoutException
                    || current instanceof SocketTimeoutException
                    || current instanceof HttpTimeoutException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private long elapsedMillis(long startedAt) {
        return Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
    }

    private void ensureAvailable() {
        if (!properties.isEnabled() || chatClient == null) {
            throw new AppException(ResponseCode.AI_MODEL_UNAVAILABLE);
        }
    }
}
