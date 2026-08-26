package com.ltcpond.datapilot.ai.rag;

/** 向量召回的一张表。 */
public record SchemaVectorMatch(String schemaName, String tableName, double score) {
}
