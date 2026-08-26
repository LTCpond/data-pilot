package com.ltcpond.datapilot.datasource.health;

import com.ltcpond.datapilot.common.health.ComponentHealth;
import com.ltcpond.datapilot.common.health.HealthProbe;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** 检查只读业务库是否可执行轻量查询。 */
@Component
public class BusinessDatabaseHealthProbe implements HealthProbe {

    private final JdbcTemplate jdbcTemplate;

    public BusinessDatabaseHealthProbe(
            @Qualifier("businessJdbcTemplate") JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public String componentName() {
        return "businessDatabase";
    }

    @Override
    public int order() {
        return 2;
    }

    @Override
    public ComponentHealth check() {
        Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
        return Integer.valueOf(1).equals(result) ? ComponentHealth.up() : ComponentHealth.down();
    }
}
