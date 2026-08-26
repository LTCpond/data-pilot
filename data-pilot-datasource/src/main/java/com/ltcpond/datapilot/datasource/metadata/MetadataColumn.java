package com.ltcpond.datapilot.datasource.metadata;

/** 从远程数据库读取的字段快照。 */
public record MetadataColumn(
        String name,
        int jdbcType,
        String nativeType,
        int ordinalPosition,
        boolean nullable,
        boolean primaryKey,
        String comment) {
}
