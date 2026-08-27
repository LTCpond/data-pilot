package com.ltcpond.datapilot.core.rag;

import com.ltcpond.datapilot.ai.rag.SchemaVectorDocument;
import com.ltcpond.datapilot.core.datasource.DatasourceSchemaView;
import com.ltcpond.datapilot.core.datasource.SchemaColumnView;
import com.ltcpond.datapilot.core.datasource.SchemaRelationView;
import com.ltcpond.datapilot.core.datasource.SchemaTableView;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** 将每张表转换为含双向外键语义的独立向量文档。 */
@Component
public class SchemaDocumentBuilder {

    /** 为数据源当前 Schema 生成可写入向量库的一组版本化文档。 */
    public List<SchemaVectorDocument> build(long datasourceId, String indexVersion, DatasourceSchemaView schema) {
        List<SchemaVectorDocument> documents = new ArrayList<>();
        for (SchemaTableView table : schema.tables()) {
            String content = content(table, schema.tables());
            String key = datasourceId + ":" + indexVersion + ":" + table.schemaName() + ":" + table.name();
            String id = UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)).toString();
            documents.add(new SchemaVectorDocument(
                    id, datasourceId, indexVersion, table.schemaName(), table.name(), content));
        }
        return List.copyOf(documents);
    }

    private String content(SchemaTableView table, List<SchemaTableView> allTables) {
        StringBuilder text = new StringBuilder("表：").append(table.name());
        appendComment(text, table.comment());
        text.append("\nSchema：").append(table.schemaName()).append("\n字段：\n");
        for (SchemaColumnView column : table.columns()) {
            text.append("- ").append(column.name()).append(' ').append(column.nativeType());
            if (column.primaryKey()) {
                text.append(" 主键");
            }
            appendComment(text, column.comment());
            text.append('\n');
        }
        List<String> relations = new ArrayList<>();
        for (SchemaTableView source : allTables) {
            for (SchemaRelationView relation : source.foreignKeys()) {
                if (sameTable(relation.sourceTable(), table)) {
                    relations.add("出向：" + relation.sourceTable() + "." + relation.sourceColumn()
                            + " -> " + relation.targetTable() + "." + relation.targetColumn());
                }
                if (sameTable(relation.targetTable(), table)) {
                    relations.add("入向：" + relation.sourceTable() + "." + relation.sourceColumn()
                            + " -> " + relation.targetTable() + "." + relation.targetColumn());
                }
            }
        }
        if (!relations.isEmpty()) {
            text.append("关联：\n");
            relations.forEach(relation -> text.append("- ").append(relation).append('\n'));
        }
        return text.toString().trim();
    }

    private boolean sameTable(String qualifiedName, SchemaTableView table) {
        return qualifiedName.equalsIgnoreCase(table.name())
                || qualifiedName.equalsIgnoreCase(table.schemaName() + "." + table.name());
    }

    private void appendComment(StringBuilder text, String comment) {
        if (comment != null && !comment.isBlank()) {
            text.append("（").append(comment.trim()).append("）");
        }
    }
}
