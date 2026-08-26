package com.ltcpond.datapilot.datasource.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * 装配两个用途不同的数据源：管理库负责项目数据，业务库只允许查询演示数据。
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({BusinessDataSourceProperties.class, EncryptionProperties.class})
public class DataSourceConfiguration {

    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource")
    public DataSourceProperties managementDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean(name = "managementDataSource")
    @Primary
    public HikariDataSource managementDataSource(
            @Qualifier("managementDataSourceProperties") DataSourceProperties properties) {
        HikariDataSource dataSource = properties.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
        dataSource.setPoolName("data-pilot-management");
        dataSource.setMaximumPoolSize(5);
        dataSource.setConnectionTimeout(3000);
        dataSource.setValidationTimeout(2000);
        return dataSource;
    }

    @Bean(name = "managementJdbcTemplate")
    @Primary
    public JdbcTemplate managementJdbcTemplate(
            @Qualifier("managementDataSource") DataSource dataSource) {
        return jdbcTemplate(dataSource);
    }

    @Bean(name = "businessDataSource", destroyMethod = "close")
    public HikariDataSource businessDataSource(BusinessDataSourceProperties properties) {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setPoolName("data-pilot-business-read-only");
        dataSource.setJdbcUrl(properties.getJdbcUrl());
        dataSource.setUsername(properties.getUsername());
        dataSource.setPassword(properties.getPassword());
        dataSource.setDriverClassName(properties.getDriverClassName());
        dataSource.setMaximumPoolSize(properties.getMaximumPoolSize());
        dataSource.setConnectionTimeout(properties.getConnectionTimeout());
        dataSource.setValidationTimeout(properties.getValidationTimeout());
        // 连接池只读标记是应用侧保护，数据库只读账号仍是最终安全边界。
        dataSource.setReadOnly(properties.isReadOnly());
        return dataSource;
    }

    @Bean(name = "businessJdbcTemplate")
    public JdbcTemplate businessJdbcTemplate(
            @Qualifier("businessDataSource") DataSource dataSource) {
        return jdbcTemplate(dataSource);
    }

    private JdbcTemplate jdbcTemplate(DataSource dataSource) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        // 避免健康检查因数据库故障长期占用请求线程。
        jdbcTemplate.setQueryTimeout(2);
        return jdbcTemplate;
    }
}
