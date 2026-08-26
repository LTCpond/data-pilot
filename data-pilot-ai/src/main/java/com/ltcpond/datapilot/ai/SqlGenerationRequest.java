package com.ltcpond.datapilot.ai;

/** 首次生成 SQL 所需的用户问题和受信任 Schema 文本。 */
public record SqlGenerationRequest(String question, String schema) {
}
