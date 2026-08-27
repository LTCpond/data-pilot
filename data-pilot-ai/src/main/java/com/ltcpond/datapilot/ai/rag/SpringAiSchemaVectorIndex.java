package com.ltcpond.datapilot.ai.rag;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/** 使用 Spring AI PgVectorStore 保存和检索 Schema 文档。 */
@Component
public class SpringAiSchemaVectorIndex implements SchemaVectorIndex {

    private final PgVectorStore vectorStore;
    private final JdbcTemplate jdbcTemplate;

    public SpringAiSchemaVectorIndex(
            ObjectProvider<PgVectorStore> vectorStoreProvider,
            @Qualifier("ragJdbcTemplate") ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
        this.vectorStore = vectorStoreProvider.getIfAvailable();
        this.jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
    }

    /** 将 Schema 文档转换为 Spring AI Document 后批量写入 PgVector。 */
    @Override
    public void index(List<SchemaVectorDocument> documents) {
        requireAvailable();
        vectorStore.add(documents.stream().map(this::toDocument).toList());
    }

    /** 使用 PgVector 相似度检索指定数据源当前索引版本内的候选表。 */
    @Override
    public List<SchemaVectorMatch> search(
            long datasourceId, String indexVersion, String question, int topK) {
        requireAvailable();
        String filter = "datasourceId == '" + datasourceId + "' && indexVersion == '" + indexVersion + "'";
        SearchRequest request = SearchRequest.builder()
                .query(question)
                .topK(topK)
                .filterExpression(filter)
                .build();
        List<Document> matches = vectorStore.similaritySearch(request);
        if (matches == null) {
            return List.of();
        }
        return matches.stream()
                .map(document -> new SchemaVectorMatch(
                        String.valueOf(document.getMetadata().get("schemaName")),
                        String.valueOf(document.getMetadata().get("tableName")),
                        document.getScore() == null ? 0D : document.getScore()))
                .toList();
    }

    /** 删除某个索引版本的所有向量记录，用于回滚失败重建。 */
    @Override
    public void deleteVersion(long datasourceId, String indexVersion) {
        requireAvailable();
        jdbcTemplate.update(
                "DELETE FROM " + PgVectorRagConfiguration.VECTOR_TABLE
                        + " WHERE metadata->>'datasourceId' = ? AND metadata->>'indexVersion' = ?",
                String.valueOf(datasourceId), indexVersion);
    }

    /** 清理当前活动版本之外的历史向量，保留线上可用版本。 */
    @Override
    public void deleteOtherVersions(long datasourceId, String activeIndexVersion) {
        requireAvailable();
        jdbcTemplate.update(
                "DELETE FROM " + PgVectorRagConfiguration.VECTOR_TABLE
                        + " WHERE metadata->>'datasourceId' = ? AND metadata->>'indexVersion' <> ?",
                String.valueOf(datasourceId), activeIndexVersion);
    }

    /** 判断 PgVectorStore 和 RAG 数据库访问是否都已可用。 */
    @Override
    public boolean available() {
        return vectorStore != null && jdbcTemplate != null;
    }

    private Document toDocument(SchemaVectorDocument document) {
        return Document.builder()
                .id(document.id())
                .text(document.content())
                .metadata(Map.of(
                        "datasourceId", String.valueOf(document.datasourceId()),
                        "indexVersion", document.indexVersion(),
                        "schemaName", document.schemaName(),
                        "tableName", document.tableName(),
                        "documentType", "SCHEMA_TABLE"))
                .build();
    }

    private void requireAvailable() {
        if (!available()) {
            throw new IllegalStateException("Schema 向量索引不可用");
        }
    }
}
