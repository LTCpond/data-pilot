package com.ltcpond.datapilot.datasource.metadata;

import java.util.List;

/** 一次完整读取产生的不可变数据库结构快照。 */
public record MetadataSnapshot(List<MetadataTable> tables, List<MetadataRelation> relations) {

    public MetadataSnapshot {
        tables = List.copyOf(tables);
        relations = List.copyOf(relations);
    }

    public int columnCount() {
        return tables.stream().mapToInt(table -> table.columns().size()).sum();
    }

    public int primaryKeyCount() {
        return (int) tables.stream()
                .flatMap(table -> table.columns().stream())
                .filter(MetadataColumn::primaryKey)
                .count();
    }
}
