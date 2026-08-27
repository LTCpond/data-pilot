package com.ltcpond.datapilot.common.health;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 系统总体健康状态及各依赖状态的只读快照。
 */
public record SystemHealth(String status, Map<String, ComponentHealth> components) {

    public SystemHealth {
        components = Collections.unmodifiableMap(new LinkedHashMap<>(components));
    }

    /** 根据各组件健康状态汇总系统总体状态。 */
    public static SystemHealth from(Map<String, ComponentHealth> components) {
        boolean allUp = components.values().stream().allMatch(ComponentHealth::healthy);
        return new SystemHealth(allUp ? "UP" : "DOWN", components);
    }

    /** 判断所有被检查组件是否均处于可用状态。 */
    public boolean healthy() {
        return "UP".equals(status);
    }
}
