package com.ltcpond.datapilot.core.datasource;

/** API 使用的字段元数据。 */
public record SchemaColumnView(
        long id,
        String name,
        int jdbcType,
        String nativeType,
        int ordinalPosition,
        boolean nullable,
        boolean primaryKey,
        String comment) {
}
