package com.ltcpond.datapilot.core.datasource;

/** API 使用的外键关系。 */
public record SchemaRelationView(
        String constraintName,
        String sourceTable,
        String sourceColumn,
        String targetTable,
        String targetColumn,
        String updateRule,
        String deleteRule) {
}
