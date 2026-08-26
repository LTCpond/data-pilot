package com.ltcpond.datapilot.ai.rag;

/** 一张 Schema 表对应的向量文档，正文和 Metadata 均不得包含连接凭据。 */
public record SchemaVectorDocument(
        String id,
        long datasourceId,
        String indexVersion,
        String schemaName,
        String tableName,
        String content) {
}
