package com.ltcpond.datapilot.datasource.health;

import com.ltcpond.datapilot.common.health.ComponentHealth;
import com.ltcpond.datapilot.common.health.HealthProbe;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** 检查 Data Pilot 管理库是否可用。 */
@Component
public class ManagementDatabaseHealthProbe implements HealthProbe {

    private final JdbcTemplate jdbcTemplate;

    public ManagementDatabaseHealthProbe(
            @Qualifier("managementJdbcTemplate") JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 返回管理库在健康响应中的稳定组件名。 */
    @Override
    public String componentName() {
        return "managementDatabase";
    }

    /** 让管理库检查在健康响应中优先展示。 */
    @Override
    public int order() {
        return 1;
    }

    /** 执行轻量 SELECT 1，验证管理库连接可用。 */
    @Override
    public ComponentHealth check() {
        Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
        return Integer.valueOf(1).equals(result) ? ComponentHealth.up() : ComponentHealth.down();
    }
}
