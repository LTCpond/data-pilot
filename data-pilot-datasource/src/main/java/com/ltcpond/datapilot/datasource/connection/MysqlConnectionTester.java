package com.ltcpond.datapilot.datasource.connection;

import com.zaxxer.hikari.HikariDataSource;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;

/** 对用户提供的 MySQL 配置执行一次只读连通性检查。 */
@Component
@RequiredArgsConstructor
public class MysqlConnectionTester {

    private final TemporaryMysqlDataSourceFactory dataSourceFactory;

    public ConnectionTestResult test(DatasourceConnectionInfo connectionInfo) {
        try (HikariDataSource dataSource = dataSourceFactory.create(connectionInfo);
             Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.setQueryTimeout(2);
            try (ResultSet resultSet = statement.executeQuery("SELECT 1")) {
                if (!resultSet.next() || resultSet.getInt(1) != 1) {
                    throw new ExternalDatasourceException();
                }
            }
            DatabaseMetaData metadata = connection.getMetaData();
            return new ConnectionTestResult(
                    metadata.getDatabaseProductName(), metadata.getDatabaseProductVersion());
        } catch (ExternalDatasourceException exception) {
            throw exception;
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ExternalDatasourceException();
        }
    }
}
