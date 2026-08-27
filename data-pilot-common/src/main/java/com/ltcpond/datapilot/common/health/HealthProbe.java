package com.ltcpond.datapilot.common.health;

/**
 * 外部依赖健康探针。实现只负责执行一次轻量检查，超时和异常隔离由核心服务处理。
 */
public interface HealthProbe {

    /** 返回稳定的组件标识，用作 API 响应中的键。 */
    String componentName();

    /** 返回展示顺序，保证健康响应的组件顺序稳定。 */
    int order();

    /** 执行一次轻量健康检查；失败原因应由调用方统一脱敏处理。 */
    ComponentHealth check();
}
