package com.ltcpond.datapilot.core.datasource;

import com.ltcpond.datapilot.common.api.ResponseCode;
import com.ltcpond.datapilot.common.exception.AppException;
import com.ltcpond.datapilot.datasource.connection.ConnectionTestResult;
import com.ltcpond.datapilot.datasource.connection.DatasourceConnectionInfo;
import com.ltcpond.datapilot.datasource.connection.MysqlConnectionTester;
import com.ltcpond.datapilot.datasource.crypto.CredentialCipher;
import com.ltcpond.datapilot.datasource.entity.DatasourceEntity;
import com.ltcpond.datapilot.datasource.entity.SchemaColumnEntity;
import com.ltcpond.datapilot.datasource.entity.SchemaRelationEntity;
import com.ltcpond.datapilot.datasource.entity.SchemaTableEntity;
import com.ltcpond.datapilot.datasource.metadata.MetadataSnapshot;
import com.ltcpond.datapilot.datasource.metadata.MysqlMetadataReader;
import com.ltcpond.datapilot.datasource.store.DatasourceStore;
import com.ltcpond.datapilot.datasource.store.StoredSchema;
import com.ltcpond.datapilot.datasource.store.SyncPersistenceResult;
import com.ltcpond.datapilot.core.rag.RagIndexResultView;
import com.ltcpond.datapilot.core.rag.SchemaIndexService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.ObjectProvider;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 数据源连接、保存和元数据同步的应用服务。 */
@Service
@RequiredArgsConstructor
public class DatasourceService {

    private final DatasourceStore store;
    private final MysqlConnectionTester connectionTester;
    private final MysqlMetadataReader metadataReader;
    private final CredentialCipher credentialCipher;
    private final ObjectProvider<SchemaIndexService> schemaIndexServiceProvider;

    public ConnectionTestView testConnection(ConnectionTestCommand command) {
        try {
            ConnectionTestResult result = connectionTester.test(connectionInfo(command));
            return new ConnectionTestView(true, result.databaseProduct(), result.databaseVersion());
        } catch (IllegalArgumentException exception) {
            throw new AppException(ResponseCode.INVALID_DATASOURCE_CONFIGURATION);
        } catch (AppException exception) {
            if (exception.getResponseCode() != ResponseCode.EXTERNAL_DATASOURCE_OPERATION_FAILED) {
                throw exception;
            }
            throw new AppException(ResponseCode.DATASOURCE_UNREACHABLE);
        }
    }

    public DatasourceView create(CreateDatasourceCommand command) {
        String normalizedName = command.name().trim();
        if (store.findByName(normalizedName).isPresent()) {
            throw new AppException(ResponseCode.DUPLICATE_DATASOURCE_NAME);
        }

        testConnection(new ConnectionTestCommand(command.jdbcUrl(), command.username(), command.password()));
        LocalDateTime now = LocalDateTime.now();
        DatasourceEntity entity = new DatasourceEntity();
        entity.setName(normalizedName);
        entity.setDescription(normalizeNullable(command.description()));
        entity.setDbType("MYSQL");
        entity.setJdbcUrl(command.jdbcUrl().trim());
        entity.setUsername(command.username().trim());
        entity.setEncryptedPassword(credentialCipher.encrypt(command.password()));
        entity.setStatus("CONNECTED");
        entity.setRagStatus("PENDING");
        entity.setRagDocumentCount(0);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        try {
            return toView(store.insert(entity));
        } catch (DuplicateKeyException exception) {
            throw new AppException(ResponseCode.DUPLICATE_DATASOURCE_NAME);
        }
    }

    public List<DatasourceView> list() {
        return store.findAll().stream().map(this::toView).toList();
    }

    public DatasourceView get(long datasourceId) {
        return toView(requiredDatasource(datasourceId));
    }

    public SyncResultView synchronize(long datasourceId) {
        DatasourceEntity datasource = requiredDatasource(datasourceId);
        try {
            String password = credentialCipher.decrypt(datasource.getEncryptedPassword());
            MetadataSnapshot snapshot = metadataReader.read(new DatasourceConnectionInfo(
                    datasource.getJdbcUrl(), datasource.getUsername(), password));
            SyncPersistenceResult result = store.synchronize(datasourceId, snapshot);
            String ragStatus = "PENDING";
            int ragDocumentCount = 0;
            store.markRagPending(datasourceId);
            try {
                RagIndexResultView index = schemaIndexServiceProvider.getObject().rebuildAfterSync(datasourceId);
                ragStatus = index.status();
                ragDocumentCount = index.documentCount();
            } catch (RuntimeException ignored) {
                ragStatus = "ERROR";
            }
            return new SyncResultView(
                    datasourceId,
                    result.tableCount(),
                    result.columnCount(),
                    result.primaryKeyCount(),
                    result.foreignKeyCount(),
                    result.syncedAt(),
                    ragStatus,
                    ragDocumentCount);
        } catch (AppException exception) {
            markErrorSafely(datasourceId);
            if (exception.getResponseCode() == ResponseCode.EXTERNAL_DATASOURCE_OPERATION_FAILED) {
                throw new AppException(ResponseCode.DATASOURCE_UNREACHABLE);
            }
            throw new AppException(ResponseCode.DATASOURCE_METADATA_SYNC_FAILED);
        } catch (RuntimeException exception) {
            markErrorSafely(datasourceId);
            throw new AppException(ResponseCode.DATASOURCE_METADATA_SYNC_FAILED);
        }
    }

    public DatasourceSchemaView schema(long datasourceId) {
        requiredDatasource(datasourceId);
        StoredSchema schema = store.loadSchema(datasourceId);

        Map<Long, SchemaTableEntity> tablesById = new HashMap<>();
        schema.tables().forEach(table -> tablesById.put(table.getId(), table));
        Map<Long, SchemaColumnEntity> columnsById = new HashMap<>();
        Map<Long, List<SchemaColumnView>> columnsByTableId = new HashMap<>();
        for (SchemaColumnEntity column : schema.columns()) {
            columnsById.put(column.getId(), column);
            columnsByTableId.computeIfAbsent(column.getTableId(), ignored -> new ArrayList<>())
                    .add(toColumnView(column));
        }

        Map<Long, List<SchemaRelationView>> relationsBySourceTableId = new HashMap<>();
        for (SchemaRelationEntity relation : schema.relations()) {
            SchemaTableEntity sourceTable = tablesById.get(relation.getSourceTableId());
            SchemaTableEntity targetTable = tablesById.get(relation.getTargetTableId());
            SchemaColumnEntity sourceColumn = columnsById.get(relation.getSourceColumnId());
            SchemaColumnEntity targetColumn = columnsById.get(relation.getTargetColumnId());
            if (sourceTable == null || targetTable == null || sourceColumn == null || targetColumn == null) {
                continue;
            }
            SchemaRelationView view = new SchemaRelationView(
                    relation.getConstraintName(),
                    qualifiedName(sourceTable),
                    sourceColumn.getColumnName(),
                    qualifiedName(targetTable),
                    targetColumn.getColumnName(),
                    relation.getUpdateRule(),
                    relation.getDeleteRule());
            relationsBySourceTableId.computeIfAbsent(sourceTable.getId(), ignored -> new ArrayList<>()).add(view);
        }

        List<SchemaTableView> tableViews = schema.tables().stream()
                .map(table -> new SchemaTableView(
                        table.getId(),
                        table.getSchemaName(),
                        table.getTableName(),
                        table.getTableType(),
                        table.getTableComment(),
                        columnsByTableId.getOrDefault(table.getId(), List.of()),
                        relationsBySourceTableId.getOrDefault(table.getId(), List.of())))
                .toList();
        return new DatasourceSchemaView(datasourceId, tableViews);
    }

    private DatasourceEntity requiredDatasource(long datasourceId) {
        return store.findById(datasourceId)
                .orElseThrow(() -> new AppException(ResponseCode.DATASOURCE_NOT_FOUND));
    }

    private DatasourceConnectionInfo connectionInfo(ConnectionTestCommand command) {
        return new DatasourceConnectionInfo(command.jdbcUrl(), command.username(), command.password());
    }

    private DatasourceView toView(DatasourceEntity entity) {
        return new DatasourceView(
                entity.getId(),
                entity.getName(),
                entity.getDescription(),
                entity.getDbType(),
                entity.getJdbcUrl(),
                entity.getUsername(),
                entity.getStatus(),
                entity.getLastSyncAt(),
                entity.getRagStatus(),
                entity.getRagIndexVersion(),
                entity.getRagDocumentCount() == null ? 0 : entity.getRagDocumentCount(),
                entity.getRagIndexedAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    private SchemaColumnView toColumnView(SchemaColumnEntity column) {
        return new SchemaColumnView(
                column.getId(),
                column.getColumnName(),
                column.getJdbcType(),
                column.getNativeType(),
                column.getOrdinalPosition(),
                Boolean.TRUE.equals(column.getNullable()),
                Boolean.TRUE.equals(column.getPrimaryKey()),
                column.getColumnComment());
    }

    private String qualifiedName(SchemaTableEntity table) {
        return table.getSchemaName() + "." + table.getTableName();
    }

    private String normalizeNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private void markErrorSafely(long datasourceId) {
        try {
            store.markError(datasourceId);
        } catch (RuntimeException ignored) {
            // 不让状态更新失败覆盖真正的同步错误。
        }
    }
}
