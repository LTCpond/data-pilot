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

/** Spring AI 的受控 Agent 决策适配器；不会自动执行任何模型动作。 */
final class SpringAiQueryAgentModel implements QueryAgentModel {

    static final String PROMPT_VERSION = "data-agent-v1";
    static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60);

    private static final String SYSTEM_PROMPT = """
            Prompt 版本：data-agent-v1。
            你是 Data Pilot 的只读数据查询 Agent。你只能返回一个 JSON 对象，不要输出 Markdown 或思维过程。
            第 1 回合必须返回 type=INTENT，intent 只能是 FETCH、TREND、COMPARISON、RANKING、AMBIGUOUS、UNSUPPORTED。
            AMBIGUOUS 必须同时给出 outcome=CLARIFY 和 clarificationQuestion；UNSUPPORTED 必须给出 outcome=UNSUPPORTED。
            其余意图在后续回合返回 type=TOOL_CALL，工具只能是 search_schema、get_schema、execute_readonly_sql。
            search_schema 使用 question/topK；get_schema 使用 tableNames；execute_readonly_sql 使用 sql。
            不得请求文件、网络、代码执行、数据库写入或其他数据源。SQL 只能是 MySQL SELECT 或 WITH...SELECT。
            收到成功 SQL 观察后可以返回 type=FINAL、outcome=ANSWER，并填写 questionAnalysis、relatedTables、explanation、confidence。
            未成功执行 SQL 前禁止返回 ANSWER。遇到失败必须根据 errorKind 修改方案，不要重复相同动作。
            FINAL outcome 只能是 ANSWER、CLARIFY、UNSUPPORTED。
            """;

    private final DataPilotAiProperties properties;
    private final ChatClient chatClient;
    private final BeanOutputConverter<AgentDecision> converter = new BeanOutputConverter<>(AgentDecision.class);

    /** 创建模型适配器；未配置模型时保留空客户端，由调用阶段返回稳定错误。 */
    SpringAiQueryAgentModel(DataPilotAiProperties properties, ChatModel chatModel) {
        this.properties = properties;
        this.chatClient = chatModel == null ? null : ChatClient.create(chatModel);
    }

    /** 请求模型生成当前回合的结构化决策，并将格式和调用异常收敛为稳定业务错误。 */
    @Override
    public AgentTurnOutcome next(AgentTurnRequest request) {
        // 在发起请求前统一检查 AI 配置与模型客户端是否可用。
        ensureAvailable();
        long startedAt = System.nanoTime();
        try {
            // 应用自行发起模型调用，只要求 JSON 决策，不启用框架自动工具执行。
            ChatResponse response = chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .user(render(request))
                    .options(options())
                    .call()
                    .chatResponse();
            // 空响应无法形成可审计的 Agent 动作，直接作为模型失败处理。
            if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
                throw failure("AI_EMPTY_RESPONSE");
            }
            String finishReason = response.getResult().getMetadata() == null
                    ? null : response.getResult().getMetadata().getFinishReason();
            if (isTruncated(finishReason)) {
                throw failure("AI_RESPONSE_TRUNCATED");
            }
            String content = response.getResult().getOutput().getText();
            if (content == null || content.isBlank()) {
                throw failure("AI_EMPTY_RESPONSE");
            }
            AgentDecision decision;
            try {
                // 严格按 AgentDecision 解析，避免把自由文本当作可执行动作。
                decision = converter.convert(content);
            } catch (RuntimeException exception) {
                throw new AppException(
                        ResponseCode.AI_SQL_GENERATION_FAILED, "AI_TOOL_CALLING_UNSUPPORTED", exception);
            }
            if (decision == null || decision.type() == null || decision.type().isBlank()) {
                throw failure("AI_TOOL_CALLING_UNSUPPORTED");
            }
            // 仅返回结构化决策和用量指标，实际工具始终由上层 Agent 调度。
            return new AgentTurnOutcome(decision, metrics(response, elapsedMillis(startedAt)));
        } catch (AppException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            String code = hasTimeoutCause(exception) ? "AI_REQUEST_TIMEOUT" : "AI_AGENT_FAILED";
            throw new AppException(ResponseCode.AI_SQL_GENERATION_FAILED, code, exception);
        }
    }

    /** 构造当前回合提示词，仅包含用户问题、已识别意图和脱敏工具观察。 */
    private String render(AgentTurnRequest request) {
        StringBuilder builder = new StringBuilder()
                .append("用户问题：").append(request.question()).append('\n')
                .append("当前回合：").append(request.turn()).append('\n')
                .append("已识别意图：").append(request.intent() == null ? "尚未识别" : request.intent()).append('\n')
                .append("脱敏工具观察：\n");
        // 只回传受控观察摘要，不把数据库完整结果或内部思维链发送给模型。
        if (request.observations().isEmpty()) {
            builder.append("无\n");
        } else {
            for (AgentObservation observation : request.observations()) {
                builder.append("- step=").append(observation.stepNo())
                        .append(", tool=").append(observation.toolName())
                        .append(", success=").append(observation.success())
                        .append(", errorKind=").append(observation.errorKind())
                        .append(", output=").append(observation.output()).append('\n');
            }
        }
        builder.append("只返回完整 JSON 对象。字段包括 type,intent,tool,question,topK,tableNames,sql,outcome,")
                .append("questionAnalysis,relatedTables,explanation,confidence,clarificationQuestion。\n")
                .append(converter.getFormat());
        return builder.toString();
    }

    /** 创建强制 JSON 输出、限制 token 数并设置单次请求超时的模型参数。 */
    private OpenAiChatOptions.Builder options() {
        OpenAiChatModel.ResponseFormat responseFormat = OpenAiChatModel.ResponseFormat.builder()
                .type(OpenAiChatModel.ResponseFormat.Type.JSON_OBJECT).build();
        return OpenAiChatOptions.builder()
                .responseFormat(responseFormat)
                .maxCompletionTokens(1600)
                .timeout(REQUEST_TIMEOUT);
    }

    /** 从模型响应元数据中提取模型名称、token 用量和调用耗时。 */
    private AiCallMetrics metrics(ChatResponse response, long durationMs) {
        ChatResponseMetadata metadata = response.getMetadata();
        Usage usage = metadata == null ? null : metadata.getUsage();
        return new AiCallMetrics(
                metadata == null || metadata.getModel() == null ? properties.getModel() : metadata.getModel(),
                PROMPT_VERSION,
                usage == null ? null : usage.getPromptTokens(),
                usage == null ? null : usage.getCompletionTokens(),
                usage == null ? null : usage.getTotalTokens(), durationMs);
    }

    /** 判断模型是否因输出长度限制而提前结束。 */
    private boolean isTruncated(String finishReason) {
        if (finishReason == null) return false;
        String normalized = finishReason.toLowerCase(Locale.ROOT);
        return normalized.equals("length") || normalized.contains("max_tokens");
    }

    /** 沿异常链判断失败是否由请求超时引起。 */
    private boolean hasTimeoutCause(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof TimeoutException
                    || current instanceof SocketTimeoutException
                    || current instanceof HttpTimeoutException) return true;
            current = current.getCause();
        }
        return false;
    }

    /** 使用单调时钟计算模型调用耗时，避免系统时间调整影响统计。 */
    private long elapsedMillis(long startedAt) {
        return Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
    }

    /** 校验 AI 功能和模型客户端是否已经就绪。 */
    private void ensureAvailable() {
        if (!properties.isEnabled() || chatClient == null) {
            throw new AppException(ResponseCode.AI_MODEL_UNAVAILABLE);
        }
    }

    /** 创建带稳定细分错误码的模型生成失败异常。 */
    private AppException failure(String code) {
        return new AppException(ResponseCode.AI_SQL_GENERATION_FAILED, code);
    }
}
