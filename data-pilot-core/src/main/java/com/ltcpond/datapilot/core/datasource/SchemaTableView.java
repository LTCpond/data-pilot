package com.ltcpond.datapilot.core.datasource;

import java.util.List;

/** API 使用的表结构，包含字段及该表发出的外键。 */
public record SchemaTableView(
        long id,
        String schemaName,
        String name,
        String type,
        String comment,
        List<SchemaColumnView> columns,
        List<SchemaRelationView> foreignKeys) {
}
