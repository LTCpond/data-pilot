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

    /** 返回业务库在健康响应中的稳定组件名。 */
    @Override
    public String componentName() {
        return "businessDatabase";
    }

    /** 让业务库检查排在管理库之后、Redis 之前。 */
    @Override
    public int order() {
        return 2;
    }

    /** 执行轻量 SELECT 1，验证只读业务库连接可用。 */
    @Override
    public ComponentHealth check() {
        Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
        return Integer.valueOf(1).equals(result) ? ComponentHealth.up() : ComponentHealth.down();
    }
}
