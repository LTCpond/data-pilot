package com.ltcpond.datapilot.ai;

/** 仅用于首轮问题消歧的历史任务摘要。 */
public record ConversationTurn(
        String question,
        String resolvedQuestion,
        String status,
        String clarificationQuestion) {
}
