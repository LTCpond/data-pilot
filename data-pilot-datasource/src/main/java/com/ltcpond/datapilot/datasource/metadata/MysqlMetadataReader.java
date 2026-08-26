package com.ltcpond.datapilot.datasource.metadata;

import com.ltcpond.datapilot.common.api.ResponseCode;
import com.ltcpond.datapilot.common.exception.AppException;
import com.ltcpond.datapilot.datasource.connection.DatasourceConnectionInfo;
import com.ltcpond.datapilot.datasource.connection.TemporaryMysqlDataSourceFactory;
import com.zaxxer.hikari.HikariDataSource;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 使用标准 JDBC DatabaseMetaData 读取 MySQL 表、字段、主键和外键。 */
@Component
@RequiredArgsConstructor
public class MysqlMetadataReader {

    private final TemporaryMysqlDataSourceFactory dataSourceFactory;

    public MetadataSnapshot read(DatasourceConnectionInfo connectionInfo) {
        try (HikariDataSource dataSource = dataSourceFactory.create(connectionInfo);
             Connection connection = dataSource.getConnection()) {
            return read(connection);
        } catch (Exception exception) {
            throw new AppException(ResponseCode.EXTERNAL_DATASOURCE_OPERATION_FAILED);
        }
    }

    MetadataSnapshot read(Connection connection) throws Exception {
        DatabaseMetaData metadata = connection.getMetaData();
        String catalog = connection.getCatalog();
        List<MetadataTable> tables = new ArrayList<>();
        List<MetadataRelation> relations = new ArrayList<>();

        try (ResultSet tableRows = metadata.getTables(catalog, null, "%", new String[]{"TABLE", "VIEW"})) {
            while (tableRows.next()) {
                String tableCatalog = valueOrFallback(tableRows.getString("TABLE_CAT"), catalog);
                String tableSchema = valueOrFallback(tableRows.getString("TABLE_SCHEM"), tableCatalog);
                String tableName = tableRows.getString("TABLE_NAME");
                Set<String> primaryKeys = readPrimaryKeys(metadata, tableCatalog, tableName);
                List<MetadataColumn> columns = readColumns(metadata, tableCatalog, tableName, primaryKeys);
                tables.add(new MetadataTable(
                        tableSchema,
                        tableName,
                        tableRows.getString("TABLE_TYPE"),
                        tableRows.getString("REMARKS"),
                        columns));
                relations.addAll(readRelations(metadata, tableCatalog, tableSchema, tableName));
            }
        }
        return new MetadataSnapshot(tables, relations);
    }

    private Set<String> readPrimaryKeys(DatabaseMetaData metadata, String catalog, String tableName)
            throws Exception {
        Set<String> primaryKeys = new HashSet<>();
        try (ResultSet rows = metadata.getPrimaryKeys(catalog, null, tableName)) {
            while (rows.next()) {
                primaryKeys.add(rows.getString("COLUMN_NAME"));
            }
        }
        return primaryKeys;
    }

    private List<MetadataColumn> readColumns(
            DatabaseMetaData metadata, String catalog, String tableName, Set<String> primaryKeys) throws Exception {
        List<MetadataColumn> columns = new ArrayList<>();
        try (ResultSet rows = metadata.getColumns(catalog, null, tableName, "%")) {
            while (rows.next()) {
                String columnName = rows.getString("COLUMN_NAME");
                columns.add(new MetadataColumn(
                        columnName,
                        rows.getInt("DATA_TYPE"),
                        rows.getString("TYPE_NAME"),
                        rows.getInt("ORDINAL_POSITION"),
                        rows.getInt("NULLABLE") == DatabaseMetaData.columnNullable,
                        primaryKeys.contains(columnName),
                        rows.getString("REMARKS")));
            }
        }
        return columns;
    }

    private List<MetadataRelation> readRelations(
            DatabaseMetaData metadata, String catalog, String schemaName, String tableName) throws Exception {
        List<MetadataRelation> relations = new ArrayList<>();
        try (ResultSet rows = metadata.getImportedKeys(catalog, null, tableName)) {
            while (rows.next()) {
                String targetCatalog = valueOrFallback(rows.getString("PKTABLE_CAT"), catalog);
                relations.add(new MetadataRelation(
                        valueOrFallback(rows.getString("FK_NAME"), "FK_" + tableName),
                        schemaName,
                        rows.getString("FKTABLE_NAME"),
                        rows.getString("FKCOLUMN_NAME"),
                        valueOrFallback(rows.getString("PKTABLE_SCHEM"), targetCatalog),
                        rows.getString("PKTABLE_NAME"),
                        rows.getString("PKCOLUMN_NAME"),
                        ruleName(rows.getShort("UPDATE_RULE")),
                        ruleName(rows.getShort("DELETE_RULE"))));
            }
        }
        return relations;
    }

    private String ruleName(short rule) {
        return switch (rule) {
            case DatabaseMetaData.importedKeyCascade -> "CASCADE";
            case DatabaseMetaData.importedKeySetNull -> "SET_NULL";
            case DatabaseMetaData.importedKeySetDefault -> "SET_DEFAULT";
            case DatabaseMetaData.importedKeyRestrict -> "RESTRICT";
            case DatabaseMetaData.importedKeyNoAction -> "NO_ACTION";
            default -> "UNKNOWN";
        };
    }

    private String valueOrFallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
