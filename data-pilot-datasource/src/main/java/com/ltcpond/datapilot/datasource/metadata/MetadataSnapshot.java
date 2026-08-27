package com.ltcpond.datapilot.datasource.metadata;

import java.util.List;

/** 一次完整读取产生的不可变数据库结构快照。 */
public record MetadataSnapshot(List<MetadataTable> tables, List<MetadataRelation> relations) {

    public MetadataSnapshot {
        tables = List.copyOf(tables);
        relations = List.copyOf(relations);
    }

    /** 统计快照内所有表的字段数量。 */
    public int columnCount() {
        return tables.stream().mapToInt(table -> table.columns().size()).sum();
    }

    /** 统计快照内被标记为主键的字段数量。 */
    public int primaryKeyCount() {
        return (int) tables.stream()
                .flatMap(table -> table.columns().stream())
                .filter(MetadataColumn::primaryKey)
                .count();
    }
}
