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

    public static SystemHealth from(Map<String, ComponentHealth> components) {
        boolean allUp = components.values().stream().allMatch(ComponentHealth::healthy);
        return new SystemHealth(allUp ? "UP" : "DOWN", components);
    }

    public boolean healthy() {
        return "UP".equals(status);
    }
}
