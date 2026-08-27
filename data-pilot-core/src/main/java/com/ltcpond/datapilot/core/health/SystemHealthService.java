package com.ltcpond.datapilot.core.health;

import com.ltcpond.datapilot.common.health.ComponentHealth;
import com.ltcpond.datapilot.common.health.HealthProbe;
import com.ltcpond.datapilot.common.health.SystemHealth;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 并行执行所有健康探针，并将异常或超时统一降级为 DOWN。
 */
@Service
public class SystemHealthService implements AutoCloseable {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(2);

    private final List<HealthProbe> probes;
    private final Duration timeout;
    private final ExecutorService executor;

    /** 使用 Spring 注入的所有探针创建健康检查服务。 */
    @Autowired
    public SystemHealthService(List<HealthProbe> probes) {
        this(probes, DEFAULT_TIMEOUT, Executors.newVirtualThreadPerTaskExecutor());
    }

    SystemHealthService(List<HealthProbe> probes, Duration timeout, ExecutorService executor) {
        this.probes = new ArrayList<>(probes);
        this.probes.sort(Comparator.comparingInt(HealthProbe::order));
        this.timeout = timeout;
        this.executor = executor;
    }

    /** 并行执行所有探针，任一探针异常或超时都会被折叠为 DOWN。 */
    public SystemHealth check() {
        Map<HealthProbe, CompletableFuture<ComponentHealth>> futures = new LinkedHashMap<>();
        for (HealthProbe probe : probes) {
            // 虚拟线程避免某个慢依赖串行阻塞其他探针；每个探针最多等待两秒。
            CompletableFuture<ComponentHealth> future = CompletableFuture
                    .supplyAsync(() -> safeCheck(probe), executor)
                    .completeOnTimeout(ComponentHealth.down(), timeout.toMillis(), TimeUnit.MILLISECONDS)
                    .exceptionally(ignored -> ComponentHealth.down());
            futures.put(probe, future);
        }

        Map<String, ComponentHealth> components = new LinkedHashMap<>();
        futures.forEach((probe, future) -> components.put(probe.componentName(), future.join()));
        return SystemHealth.from(components);
    }

    private ComponentHealth safeCheck(HealthProbe probe) {
        try {
            ComponentHealth result = probe.check();
            return result == null ? ComponentHealth.down() : result;
        } catch (RuntimeException exception) {
            // 对外仅返回 DOWN，禁止把 JDBC 地址、账号或底层异常带入响应。
            return ComponentHealth.down();
        }
    }

    /** 关闭健康检查使用的执行器，避免应用退出时遗留线程。 */
    @Override
    public void close() {
        executor.shutdownNow();
    }
}
