package com.ltcpond.datapilot.datasource.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 演示业务库连接池配置，与 Spring 管理库数据源完全隔离。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "data-pilot.business-datasource")
public class BusinessDataSourceProperties {

    private String jdbcUrl;
    private String username;
    private String password;
    private String driverClassName = "com.mysql.cj.jdbc.Driver";
    private int maximumPoolSize = 5;
    private long connectionTimeout = 3000;
    private long validationTimeout = 2000;
    private boolean readOnly = true;
}
