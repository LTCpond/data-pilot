package com.ltcpond.datapilot.datasource.query;

import com.ltcpond.datapilot.common.api.ResponseCode;
import com.ltcpond.datapilot.common.exception.AppException;
import com.ltcpond.datapilot.datasource.connection.DatasourceConnectionInfo;
import com.ltcpond.datapilot.datasource.connection.TemporaryMysqlDataSourceFactory;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLNonTransientConnectionException;
import java.sql.Statement;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MysqlReadOnlyQueryExecutorTest {

    @Test
    void shouldExplainAndExecuteWithHardLimits() throws Exception {
        TemporaryMysqlDataSourceFactory factory = mock(TemporaryMysqlDataSourceFactory.class);
        HikariDataSource dataSource = mock(HikariDataSource.class);
        Connection connection = mock(Connection.class);
        Statement explainStatement = mock(Statement.class);
        Statement queryStatement = mock(Statement.class);
        ResultSet explainResult = mock(ResultSet.class);
        ResultSet queryResult = mock(ResultSet.class);
        ResultSetMetaData metadata = mock(ResultSetMetaData.class);
        when(factory.create(any())).thenReturn(dataSource);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(explainStatement, queryStatement);
        when(explainStatement.executeQuery(any())).thenReturn(explainResult);
        when(queryStatement.executeQuery(any())).thenReturn(queryResult);
        when(queryResult.getMetaData()).thenReturn(metadata);
        when(metadata.getColumnCount()).thenReturn(2);
        when(metadata.getColumnLabel(1)).thenReturn("product_name");
        when(metadata.getColumnLabel(2)).thenReturn("sales_amount");
        when(queryResult.next()).thenReturn(true, false);
        when(queryResult.getObject(1)).thenReturn("机械键盘");
        when(queryResult.getObject(2)).thenReturn(new BigDecimal("999.00"));

        MysqlReadOnlyQueryExecutor executor = new MysqlReadOnlyQueryExecutor(factory);
        QueryExecutionResult result = executor.execute(connectionInfo(),
                "SELECT name AS product_name, price AS sales_amount FROM products LIMIT 100", 100);

        assertThat(result.columns()).containsExactly("product_name", "sales_amount");
        assertThat(result.rows()).singleElement().satisfies(row -> {
            assertThat(row).containsEntry("product_name", "机械键盘");
            assertThat(row).containsEntry("sales_amount", new BigDecimal("999.00"));
        });
        verify(connection).setReadOnly(true);
        verify(explainStatement).setQueryTimeout(5);
        verify(explainStatement).executeQuery(
                "EXPLAIN SELECT name AS product_name, price AS sales_amount FROM products LIMIT 100");
        verify(queryStatement).setQueryTimeout(5);
        verify(queryStatement).setMaxRows(100);
    }

    @Test
    void shouldExposeOnlySanitizedConnectionErrorCode() throws Exception {
        TemporaryMysqlDataSourceFactory factory = mock(TemporaryMysqlDataSourceFactory.class);
        HikariDataSource dataSource = mock(HikariDataSource.class);
        when(factory.create(any())).thenReturn(dataSource);
        when(dataSource.getConnection()).thenThrow(new SQLNonTransientConnectionException(
                "jdbc:mysql://secret-host failed for secret-user", "08001"));
        MysqlReadOnlyQueryExecutor executor = new MysqlReadOnlyQueryExecutor(factory);

        assertThatThrownBy(() -> executor.execute(connectionInfo(), "SELECT 1", 100))
                .isInstanceOfSatisfying(AppException.class, exception -> {
                    assertThat(exception.getResponseCode()).isEqualTo(
                            ResponseCode.READ_ONLY_QUERY_EXECUTION_FAILED);
                    assertThat(exception.getDetailCode()).isEqualTo("CONNECTION_FAILED");
                    assertThat(exception.getMessage())
                            .doesNotContain("secret-host")
                            .doesNotContain("secret-user");
                });
    }

    @Test
    void shouldCancelActiveStatementByTaskId() throws Exception {
        TemporaryMysqlDataSourceFactory factory = mock(TemporaryMysqlDataSourceFactory.class);
        HikariDataSource dataSource = mock(HikariDataSource.class);
        Connection connection = mock(Connection.class);
        Statement explainStatement = mock(Statement.class);
        Statement queryStatement = mock(Statement.class);
        ResultSet explainResult = mock(ResultSet.class);
        ResultSet queryResult = mock(ResultSet.class);
        ResultSetMetaData metadata = mock(ResultSetMetaData.class);
        CountDownLatch queryStarted = new CountDownLatch(1);
        CountDownLatch cancelled = new CountDownLatch(1);
        when(factory.create(any())).thenReturn(dataSource);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(explainStatement, queryStatement);
        when(explainStatement.executeQuery(any())).thenReturn(explainResult);
        when(queryStatement.executeQuery(any())).thenAnswer(ignored -> {
            queryStarted.countDown();
            cancelled.await(5, TimeUnit.SECONDS);
            return queryResult;
        });
        doAnswer(ignored -> {
            cancelled.countDown();
            return null;
        }).when(queryStatement).cancel();
        when(queryResult.getMetaData()).thenReturn(metadata);
        when(metadata.getColumnCount()).thenReturn(0);
        when(queryResult.next()).thenReturn(false);
        MysqlReadOnlyQueryExecutor executor = new MysqlReadOnlyQueryExecutor(factory);

        CompletableFuture<QueryExecutionResult> future = CompletableFuture.supplyAsync(() ->
                executor.execute(connectionInfo(), "SELECT SLEEP(5)", 1, 99L));
        assertThat(queryStarted.await(5, TimeUnit.SECONDS)).isTrue();
        executor.cancel(99L);

        assertThat(future.get(5, TimeUnit.SECONDS).rows()).isEmpty();
        verify(queryStatement).cancel();
    }

    private DatasourceConnectionInfo connectionInfo() {
        return new DatasourceConnectionInfo(
                "jdbc:mysql://127.0.0.1:3307/ecommerce_demo", "reader", "database-secret");
    }
}
