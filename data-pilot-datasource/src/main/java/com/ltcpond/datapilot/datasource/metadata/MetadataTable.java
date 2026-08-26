package com.ltcpond.datapilot.datasource.metadata;

import java.util.List;

/** 从远程数据库读取的表或视图快照。 */
public record MetadataTable(
        String schemaName,
        String name,
        String type,
        String comment,
        List<MetadataColumn> columns) {

    public MetadataTable {
        columns = List.copyOf(columns);
    }
}
