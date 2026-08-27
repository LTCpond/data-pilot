package com.ltcpond.datapilot.core.query;

import com.ltcpond.datapilot.core.datasource.DatasourceSchemaView;
import com.ltcpond.datapilot.core.datasource.SchemaColumnView;
import com.ltcpond.datapilot.core.datasource.SchemaRelationView;
import com.ltcpond.datapilot.core.datasource.SchemaTableView;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** 将管理库元数据压缩成模型可读文本，同时构造 SQL 校验使用的表白名单。 */
@Component
public class SchemaPromptBuilder {

    /** 将表、字段和外键关系转换为模型提示词中的 Schema 文本。 */
    public String build(DatasourceSchemaView schema) {
        StringBuilder prompt = new StringBuilder();
        for (SchemaTableView table : schema.tables()) {
            prompt.append("表：").append(table.name());
            appendComment(prompt, table.comment());
            prompt.append("\n字段：\n");
            for (SchemaColumnView column : table.columns()) {
                prompt.append("- ").append(column.name()).append(' ')
                        .append(column.nativeType());
                if (column.primaryKey()) {
                    prompt.append(" 主键");
                }
                if (!column.nullable()) {
                    prompt.append(" 非空");
                }
                appendComment(prompt, column.comment());
                prompt.append('\n');
            }
            if (!table.foreignKeys().isEmpty()) {
                prompt.append("外键：\n");
                for (SchemaRelationView relation : table.foreignKeys()) {
                    prompt.append("- ")
                            .append(relation.sourceTable()).append('.').append(relation.sourceColumn())
                            .append(" -> ")
                            .append(relation.targetTable()).append('.').append(relation.targetColumn())
                            .append('\n');
                }
            }
            prompt.append('\n');
        }
        return prompt.toString().trim();
    }

    /** 生成 SQL 安全校验使用的允许访问表集合，包含简单表名和 schema.表名。 */
    public Set<String> allowedTables(DatasourceSchemaView schema) {
        Set<String> tables = new LinkedHashSet<>();
        for (SchemaTableView table : schema.tables()) {
            tables.add(table.name());
            tables.add(table.schemaName() + "." + table.name());
        }
        return Set.copyOf(tables);
    }

    /** 生成 SQL 安全校验使用的表到字段白名单映射。 */
    public Map<String, Set<String>> allowedColumns(DatasourceSchemaView schema) {
        Map<String, Set<String>> columnsByTable = new LinkedHashMap<>();
        for (SchemaTableView table : schema.tables()) {
            Set<String> columns = table.columns().stream()
                    .map(column -> column.name().toLowerCase(Locale.ROOT))
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
            columnsByTable.put(table.name().toLowerCase(Locale.ROOT), columns);
            columnsByTable.put(
                    (table.schemaName() + "." + table.name()).toLowerCase(Locale.ROOT), columns);
        }
        return Map.copyOf(columnsByTable);
    }

    private void appendComment(StringBuilder builder, String comment) {
        if (comment != null && !comment.isBlank()) {
            builder.append("（").append(comment.trim()).append("）");
        }
    }
}
