package com.ltcpond.datapilot.datasource.connection;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.stereotype.Component;

import java.util.Locale;

/** 创建使用后立即关闭的 MySQL 只读连接池。 */
@Component
public class TemporaryMysqlDataSourceFactory {

    private static final String MYSQL_PREFIX = "jdbc:mysql://";

    public HikariDataSource create(DatasourceConnectionInfo connectionInfo) {
        validateJdbcUrl(connectionInfo.jdbcUrl());

        HikariConfig config = new HikariConfig();
        config.setPoolName("data-pilot-temporary-mysql");
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");
        config.setJdbcUrl(withMetadataRemarks(connectionInfo.jdbcUrl()));
        config.setUsername(connectionInfo.username());
        config.setPassword(connectionInfo.password());
        config.setMaximumPoolSize(2);
        config.setMinimumIdle(0);
        config.setConnectionTimeout(3000);
        config.setValidationTimeout(2000);
        config.setReadOnly(true);
        return new HikariDataSource(config);
    }

    public void validateJdbcUrl(String jdbcUrl) {
        if (jdbcUrl == null || !jdbcUrl.toLowerCase(Locale.ROOT).startsWith(MYSQL_PREFIX)) {
            throw new IllegalArgumentException("仅支持 jdbc:mysql:// 格式的连接地址");
        }
    }

    private String withMetadataRemarks(String jdbcUrl) {
        if (jdbcUrl.toLowerCase(Locale.ROOT).contains("useinformationschema=")) {
            return jdbcUrl;
        }
        return jdbcUrl + (jdbcUrl.contains("?") ? "&" : "?") + "useInformationSchema=true";
    }
}
