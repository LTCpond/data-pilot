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
 * 装配管理库数据源，用于保存项目配置、任务和元数据。
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(EncryptionProperties.class)
public class DataSourceConfiguration {

    /** 绑定管理库连接配置，作为默认数据源属性。 */
    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource")
    public DataSourceProperties managementDataSourceProperties() {
        return new DataSourceProperties();
    }

    /** 创建用于保存项目配置、任务和元数据的管理库连接池。 */
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

    /** 创建管理库 JdbcTemplate，供 store 和迁移后的业务读写使用。 */
    @Bean(name = "managementJdbcTemplate")
    @Primary
    public JdbcTemplate managementJdbcTemplate(
            @Qualifier("managementDataSource") DataSource dataSource) {
        return jdbcTemplate(dataSource);
    }

    private JdbcTemplate jdbcTemplate(DataSource dataSource) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        // 避免健康检查因数据库故障长期占用请求线程。
        jdbcTemplate.setQueryTimeout(2);
        return jdbcTemplate;
    }
}
