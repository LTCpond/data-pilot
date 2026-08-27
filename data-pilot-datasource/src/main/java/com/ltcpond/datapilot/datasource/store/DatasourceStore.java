package com.ltcpond.datapilot.datasource.store;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ltcpond.datapilot.datasource.entity.DatasourceEntity;
import com.ltcpond.datapilot.datasource.entity.SchemaColumnEntity;
import com.ltcpond.datapilot.datasource.entity.SchemaRelationEntity;
import com.ltcpond.datapilot.datasource.entity.SchemaTableEntity;
import com.ltcpond.datapilot.datasource.mapper.DatasourceMapper;
import com.ltcpond.datapilot.datasource.mapper.SchemaColumnMapper;
import com.ltcpond.datapilot.datasource.mapper.SchemaRelationMapper;
import com.ltcpond.datapilot.datasource.mapper.SchemaTableMapper;
import com.ltcpond.datapilot.datasource.metadata.MetadataColumn;
import com.ltcpond.datapilot.datasource.metadata.MetadataRelation;
import com.ltcpond.datapilot.datasource.metadata.MetadataSnapshot;
import com.ltcpond.datapilot.datasource.metadata.MetadataTable;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** 封装管理库数据源配置和元数据的持久化操作。 */
@Repository
@RequiredArgsConstructor
public class DatasourceStore {

    private final DatasourceMapper datasourceMapper;
    private final SchemaTableMapper tableMapper;
    private final SchemaColumnMapper columnMapper;
    private final SchemaRelationMapper relationMapper;

    /** 按主键查找数据源配置。 */
    public Optional<DatasourceEntity> findById(long id) {
        return Optional.ofNullable(datasourceMapper.selectById(id));
    }

    /** 按唯一名称查找数据源配置。 */
    public Optional<DatasourceEntity> findByName(String name) {
        return Optional.ofNullable(datasourceMapper.selectOne(Wrappers.<DatasourceEntity>lambdaQuery()
                .eq(DatasourceEntity::getName, name)));
    }

    /** 按 ID 升序列出所有数据源配置。 */
    public List<DatasourceEntity> findAll() {
        return datasourceMapper.selectList(Wrappers.<DatasourceEntity>lambdaQuery()
                .orderByAsc(DatasourceEntity::getId));
    }

    /** 插入数据源配置，并返回带自增 ID 的实体。 */
    public DatasourceEntity insert(DatasourceEntity datasource) {
        datasourceMapper.insert(datasource);
        return datasource;
    }

    /** 将数据源标记为不可用，通常用于同步或连接失败后的状态收敛。 */
    public void markError(long datasourceId) {
        DatasourceEntity update = new DatasourceEntity();
        update.setId(datasourceId);
        update.setStatus("ERROR");
        update.setUpdatedAt(LocalDateTime.now());
        datasourceMapper.updateById(update);
    }

    /** 标记数据源的 RAG 索引正在重建。 */
    public void markRagIndexing(long datasourceId) {
        DatasourceEntity update = new DatasourceEntity();
        update.setId(datasourceId);
        update.setRagStatus("INDEXING");
        update.setRagErrorCode(null);
        update.setUpdatedAt(LocalDateTime.now());
        datasourceMapper.updateById(update);
    }

    /** 标记 RAG 索引重建成功，并切换到新的活动索引版本。 */
    public void markRagReady(
            long datasourceId, String indexVersion, int documentCount, LocalDateTime indexedAt) {
        DatasourceEntity update = new DatasourceEntity();
        update.setId(datasourceId);
        update.setRagStatus("READY");
        update.setRagIndexVersion(indexVersion);
        update.setRagDocumentCount(documentCount);
        update.setRagIndexedAt(indexedAt);
        update.setRagErrorCode(null);
        update.setUpdatedAt(indexedAt);
        datasourceMapper.updateById(update);
    }

    /** 只记录向量故障，不改变数据源 READY 状态和上一次成功版本。 */
    public void markRagError(long datasourceId, String errorCode) {
        DatasourceEntity update = new DatasourceEntity();
        update.setId(datasourceId);
        update.setRagStatus("ERROR");
        update.setRagErrorCode(errorCode);
        update.setUpdatedAt(LocalDateTime.now());
        datasourceMapper.updateById(update);
    }

    /** 在元数据变更后把 RAG 状态重置为待索引。 */
    public void markRagPending(long datasourceId) {
        DatasourceEntity update = new DatasourceEntity();
        update.setId(datasourceId);
        update.setRagStatus("PENDING");
        update.setRagErrorCode(null);
        update.setUpdatedAt(LocalDateTime.now());
        datasourceMapper.updateById(update);
    }

    /** 从管理库装载数据源最近一次同步的完整 Schema 快照。 */
    public StoredSchema loadSchema(long datasourceId) {
        List<SchemaTableEntity> tables = tableMapper.selectList(Wrappers.<SchemaTableEntity>lambdaQuery()
                .eq(SchemaTableEntity::getDatasourceId, datasourceId)
                .orderByAsc(SchemaTableEntity::getSchemaName, SchemaTableEntity::getTableName));
        List<Long> tableIds = tables.stream().map(SchemaTableEntity::getId).toList();
        List<SchemaColumnEntity> columns = tableIds.isEmpty()
                ? List.of()
                : columnMapper.selectList(Wrappers.<SchemaColumnEntity>lambdaQuery()
                        .in(SchemaColumnEntity::getTableId, tableIds)
                        .orderByAsc(SchemaColumnEntity::getTableId, SchemaColumnEntity::getOrdinalPosition));
        List<SchemaRelationEntity> relations = relationMapper.selectList(
                Wrappers.<SchemaRelationEntity>lambdaQuery()
                        .eq(SchemaRelationEntity::getDatasourceId, datasourceId)
                        .orderByAsc(SchemaRelationEntity::getId));
        return new StoredSchema(tables, columns, relations);
    }

    /**
     * 使用完整快照更新管理库。远程读取在事务外完成，因此这里失败时旧元数据会整体保留。
     */
    @Transactional
    public SyncPersistenceResult synchronize(long datasourceId, MetadataSnapshot snapshot) {
        LocalDateTime now = LocalDateTime.now();
        Map<String, SchemaTableEntity> existingTables = new HashMap<>();
        tableMapper.selectList(Wrappers.<SchemaTableEntity>lambdaQuery()
                        .eq(SchemaTableEntity::getDatasourceId, datasourceId))
                .forEach(table -> existingTables.put(tableKey(table.getSchemaName(), table.getTableName()), table));

        // 关系依赖表和字段，先清理后按照最新快照重建。
        relationMapper.delete(Wrappers.<SchemaRelationEntity>lambdaQuery()
                .eq(SchemaRelationEntity::getDatasourceId, datasourceId));

        Set<Long> retainedTableIds = new HashSet<>();
        Map<String, SchemaTableEntity> synchronizedTables = new HashMap<>();
        Map<String, SchemaColumnEntity> synchronizedColumns = new HashMap<>();

        for (MetadataTable metadataTable : snapshot.tables()) {
            String tableKey = tableKey(metadataTable.schemaName(), metadataTable.name());
            SchemaTableEntity table = existingTables.get(tableKey);
            if (table == null) {
                table = new SchemaTableEntity();
                table.setDatasourceId(datasourceId);
                table.setSchemaName(metadataTable.schemaName());
                table.setTableName(metadataTable.name());
                table.setCreatedAt(now);
            }
            table.setTableType(metadataTable.type());
            table.setTableComment(metadataTable.comment());
            table.setUpdatedAt(now);
            if (table.getId() == null) {
                tableMapper.insert(table);
            } else {
                tableMapper.updateById(table);
            }
            retainedTableIds.add(table.getId());
            synchronizedTables.put(tableKey, table);
            synchronizeColumns(table, metadataTable.columns(), now, synchronizedColumns);
        }

        List<Long> staleTableIds = existingTables.values().stream()
                .map(SchemaTableEntity::getId)
                .filter(id -> !retainedTableIds.contains(id))
                .toList();
        if (!staleTableIds.isEmpty()) {
            tableMapper.deleteByIds(staleTableIds);
        }

        for (MetadataRelation metadataRelation : snapshot.relations()) {
            SchemaTableEntity sourceTable = requiredTable(
                    synchronizedTables, metadataRelation.sourceSchema(), metadataRelation.sourceTable());
            SchemaTableEntity targetTable = requiredTable(
                    synchronizedTables, metadataRelation.targetSchema(), metadataRelation.targetTable());
            SchemaColumnEntity sourceColumn = requiredColumn(
                    synchronizedColumns, sourceTable.getId(), metadataRelation.sourceColumn());
            SchemaColumnEntity targetColumn = requiredColumn(
                    synchronizedColumns, targetTable.getId(), metadataRelation.targetColumn());

            SchemaRelationEntity relation = new SchemaRelationEntity();
            relation.setDatasourceId(datasourceId);
            relation.setConstraintName(metadataRelation.constraintName());
            relation.setSourceTableId(sourceTable.getId());
            relation.setSourceColumnId(sourceColumn.getId());
            relation.setTargetTableId(targetTable.getId());
            relation.setTargetColumnId(targetColumn.getId());
            relation.setUpdateRule(metadataRelation.updateRule());
            relation.setDeleteRule(metadataRelation.deleteRule());
            relation.setCreatedAt(now);
            relationMapper.insert(relation);
        }

        DatasourceEntity datasourceUpdate = new DatasourceEntity();
        datasourceUpdate.setId(datasourceId);
        datasourceUpdate.setStatus("READY");
        datasourceUpdate.setLastSyncAt(now);
        datasourceUpdate.setUpdatedAt(now);
        datasourceMapper.updateById(datasourceUpdate);

        return new SyncPersistenceResult(
                snapshot.tables().size(),
                snapshot.columnCount(),
                snapshot.primaryKeyCount(),
                snapshot.relations().size(),
                now);
    }

    private void synchronizeColumns(
            SchemaTableEntity table,
            List<MetadataColumn> metadataColumns,
            LocalDateTime now,
            Map<String, SchemaColumnEntity> synchronizedColumns) {
        Map<String, SchemaColumnEntity> existingColumns = new HashMap<>();
        columnMapper.selectList(Wrappers.<SchemaColumnEntity>lambdaQuery()
                        .eq(SchemaColumnEntity::getTableId, table.getId()))
                .forEach(column -> existingColumns.put(column.getColumnName(), column));

        Set<Long> retainedColumnIds = new HashSet<>();
        for (MetadataColumn metadataColumn : metadataColumns) {
            SchemaColumnEntity column = existingColumns.get(metadataColumn.name());
            if (column == null) {
                column = new SchemaColumnEntity();
                column.setTableId(table.getId());
                column.setColumnName(metadataColumn.name());
                column.setCreatedAt(now);
            }
            column.setJdbcType(metadataColumn.jdbcType());
            column.setNativeType(metadataColumn.nativeType());
            column.setOrdinalPosition(metadataColumn.ordinalPosition());
            column.setNullable(metadataColumn.nullable());
            column.setPrimaryKey(metadataColumn.primaryKey());
            column.setColumnComment(metadataColumn.comment());
            column.setUpdatedAt(now);
            if (column.getId() == null) {
                columnMapper.insert(column);
            } else {
                columnMapper.updateById(column);
            }
            retainedColumnIds.add(column.getId());
            synchronizedColumns.put(columnKey(table.getId(), column.getColumnName()), column);
        }

        List<Long> staleColumnIds = existingColumns.values().stream()
                .map(SchemaColumnEntity::getId)
                .filter(id -> !retainedColumnIds.contains(id))
                .toList();
        if (!staleColumnIds.isEmpty()) {
            columnMapper.deleteByIds(staleColumnIds);
        }
    }

    private SchemaTableEntity requiredTable(
            Map<String, SchemaTableEntity> tables, String schemaName, String tableName) {
        SchemaTableEntity table = tables.get(tableKey(schemaName, tableName));
        if (table == null) {
                throw new IllegalStateException("外键引用了未知的表");
        }
        return table;
    }

    private SchemaColumnEntity requiredColumn(
            Map<String, SchemaColumnEntity> columns, long tableId, String columnName) {
        SchemaColumnEntity column = columns.get(columnKey(tableId, columnName));
        if (column == null) {
                throw new IllegalStateException("外键引用了未知的字段");
        }
        return column;
    }

    private String tableKey(String schemaName, String tableName) {
        return schemaName.length() + ":" + schemaName + tableName;
    }

    private String columnKey(long tableId, String columnName) {
        return tableId + ":" + columnName;
    }
}
