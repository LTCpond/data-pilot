package com.ltcpond.datapilot.common.health;

/**
 * 单个外部依赖的健康状态；有意不携带异常详情，防止泄露连接信息。
 */
public record ComponentHealth(String status) {

    /** 创建表示组件可用的健康状态。 */
    public static ComponentHealth up() {
        return new ComponentHealth("UP");
    }

    /** 创建表示组件不可用的健康状态。 */
    public static ComponentHealth down() {
        return new ComponentHealth("DOWN");
    }

    /** 判断当前组件是否处于可用状态。 */
    public boolean healthy() {
        return "UP".equals(status);
    }
}
