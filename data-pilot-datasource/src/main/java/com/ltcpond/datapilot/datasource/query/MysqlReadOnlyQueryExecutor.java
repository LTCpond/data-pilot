package com.ltcpond.datapilot.datasource.query;

import com.ltcpond.datapilot.common.api.ResponseCode;
import com.ltcpond.datapilot.common.exception.AppException;
import com.ltcpond.datapilot.datasource.connection.DatasourceConnectionInfo;
import com.ltcpond.datapilot.datasource.connection.TemporaryMysqlDataSourceFactory;
import com.zaxxer.hikari.HikariDataSource;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 使用临时只读连接池执行 EXPLAIN 和最终查询，调用结束立即释放连接。 */
@Component
@RequiredArgsConstructor
public class MysqlReadOnlyQueryExecutor implements ReadOnlyQueryExecutor {

    private static final int QUERY_TIMEOUT_SECONDS = 5;

    private final TemporaryMysqlDataSourceFactory dataSourceFactory;
    private final Map<Long, Statement> activeStatements = new ConcurrentHashMap<>();

    /** 执行不需要取消跟踪的只读查询。 */
    @Override
    public QueryExecutionResult execute(
            DatasourceConnectionInfo connectionInfo,
            String sql,
            int maxRows) {
        return execute(connectionInfo, sql, maxRows, 0L);
    }

    /** 先 EXPLAIN 再执行 SQL，并注册任务 Statement 以支持取消。 */
    @Override
    public QueryExecutionResult execute(
            DatasourceConnectionInfo connectionInfo,
            String sql,
            int maxRows,
            long taskId) {
        try (HikariDataSource dataSource = dataSourceFactory.create(connectionInfo);
             Connection connection = dataSource.getConnection()) {
            connection.setReadOnly(true);
            explain(connection, sql, taskId);
            return query(connection, sql, maxRows, taskId);
        } catch (SQLException | RuntimeException exception) {
            if (exception instanceof AppException appException) {
                throw appException;
            }
            throw new AppException(
                    ResponseCode.READ_ONLY_QUERY_EXECUTION_FAILED, classify(exception), exception);
        }
    }

    /** 尽力取消指定任务当前正在执行的 Statement。 */
    @Override
    public void cancel(long taskId) {
        Statement statement = activeStatements.get(taskId);
        if (statement == null) {
            return;
        }
        try {
            statement.cancel();
        } catch (SQLException ignored) {
            // 取消是尽力而为，原查询线程仍会通过超时和只读账号收敛。
        }
    }

    private void explain(Connection connection, String sql, long taskId) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            register(taskId, statement);
            try {
                statement.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                statement.setMaxRows(1);
                try (ResultSet ignored = statement.executeQuery("EXPLAIN " + sql)) {
                    // 成功获得 ResultSet 即代表数据库能够解析并规划该查询。
                }
            } finally {
                unregister(taskId, statement);
            }
        }
    }

    private QueryExecutionResult query(Connection connection, String sql, int maxRows, long taskId) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            register(taskId, statement);
            try {
                statement.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                statement.setMaxRows(maxRows);
                try (ResultSet resultSet = statement.executeQuery(sql)) {
                    ResultSetMetaData metadata = resultSet.getMetaData();
                    List<String> columns = uniqueColumnLabels(metadata);
                    List<Map<String, Object>> rows = new ArrayList<>();
                    while (resultSet.next() && rows.size() < maxRows) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        for (int index = 1; index <= columns.size(); index++) {
                            row.put(columns.get(index - 1), serializableValue(resultSet.getObject(index)));
                        }
                        rows.add(row);
                    }
                    return new QueryExecutionResult(columns, rows);
                }
            } finally {
                unregister(taskId, statement);
            }
        }
    }

    private void register(long taskId, Statement statement) {
        if (taskId > 0) {
            activeStatements.put(taskId, statement);
        }
    }

    private void unregister(long taskId, Statement statement) {
        if (taskId > 0) {
            activeStatements.remove(taskId, statement);
        }
    }

    private List<String> uniqueColumnLabels(ResultSetMetaData metadata) throws SQLException {
        List<String> labels = new ArrayList<>();
        Map<String, Integer> occurrences = new LinkedHashMap<>();
        for (int index = 1; index <= metadata.getColumnCount(); index++) {
            String baseLabel = metadata.getColumnLabel(index);
            int occurrence = occurrences.merge(baseLabel, 1, Integer::sum);
            labels.add(occurrence == 1 ? baseLabel : baseLabel + "_" + occurrence);
        }
        return labels;
    }

    private Object serializableValue(Object value) {
        return value instanceof byte[] bytes ? Base64.getEncoder().encodeToString(bytes) : value;
    }

    private String classify(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof SQLException sqlException) {
                String sqlState = sqlException.getSQLState();
                if (sqlState != null && sqlState.startsWith("42")) {
                    return "INVALID_SQL";
                }
                if (sqlState != null && sqlState.startsWith("08")) {
                    return "CONNECTION_FAILED";
                }
                if (sqlState != null && (sqlState.startsWith("HYT") || sqlState.equals("70100"))) {
                    return "QUERY_TIMEOUT";
                }
            }
            current = current.getCause();
        }
        return "QUERY_EXECUTION_FAILED";
    }
}
