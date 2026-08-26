package com.ltcpond.datapilot.datasource.metadata;

/** 从远程数据库读取的一列外键关系。 */
public record MetadataRelation(
        String constraintName,
        String sourceSchema,
        String sourceTable,
        String sourceColumn,
        String targetSchema,
        String targetTable,
        String targetColumn,
        String updateRule,
        String deleteRule) {
}
