package com.ltcpond.datapilot.common.health;

/**
 * 单个外部依赖的健康状态；有意不携带异常详情，防止泄露连接信息。
 */
public record ComponentHealth(String status) {

    public static ComponentHealth up() {
        return new ComponentHealth("UP");
    }

    public static ComponentHealth down() {
        return new ComponentHealth("DOWN");
    }

    public boolean healthy() {
        return "UP".equals(status);
    }
}
