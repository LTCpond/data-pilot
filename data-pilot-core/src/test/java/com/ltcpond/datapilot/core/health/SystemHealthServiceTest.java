package com.ltcpond.datapilot.core.health;

import com.ltcpond.datapilot.common.health.ComponentHealth;
import com.ltcpond.datapilot.common.health.HealthProbe;
import com.ltcpond.datapilot.common.health.SystemHealth;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

class SystemHealthServiceTest {

    @Test
    void shouldReportUpWhenEveryProbeIsUp() {
        try (SystemHealthService service = serviceWith(
                probe("managementDatabase", 1, ComponentHealth.up()),
                probe("businessDatabase", 2, ComponentHealth.up()),
                probe("redis", 3, ComponentHealth.up()))) {
            SystemHealth health = service.check();

            assertThat(health.status()).isEqualTo("UP");
            assertThat(health.components()).containsOnlyKeys(
                    "managementDatabase", "businessDatabase", "redis");
            assertThat(health.components().values()).allMatch(ComponentHealth::healthy);
        }
    }

    @Test
    void shouldReportDownWithoutExposingProbeException() {
        HealthProbe failingProbe = new HealthProbe() {
            @Override
            public String componentName() {
                return "redis";
            }

            @Override
            public int order() {
                return 1;
            }

            @Override
            public ComponentHealth check() {
                throw new IllegalStateException("secret connection details");
            }
        };

        try (SystemHealthService service = serviceWith(failingProbe)) {
            SystemHealth health = service.check();

            assertThat(health.status()).isEqualTo("DOWN");
            assertThat(health.components().get("redis").status()).isEqualTo("DOWN");
            assertThat(health.toString()).doesNotContain("secret connection details");
        }
    }

    @Test
    void shouldMarkSlowProbeDownAfterTimeout() {
        HealthProbe slowProbe = new HealthProbe() {
            @Override
            public String componentName() {
                return "businessDatabase";
            }

            @Override
            public int order() {
                return 1;
            }

            @Override
            public ComponentHealth check() {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
                return ComponentHealth.up();
            }
        };

        try (SystemHealthService service = new SystemHealthService(
                List.of(slowProbe), Duration.ofMillis(30), Executors.newVirtualThreadPerTaskExecutor())) {
            SystemHealth health = service.check();

            assertThat(health.status()).isEqualTo("DOWN");
            assertThat(health.components().get("businessDatabase").status()).isEqualTo("DOWN");
        }
    }

    private SystemHealthService serviceWith(HealthProbe... probes) {
        return new SystemHealthService(
                List.of(probes), Duration.ofSeconds(1), Executors.newVirtualThreadPerTaskExecutor());
    }

    private HealthProbe probe(String name, int order, ComponentHealth health) {
        return new HealthProbe() {
            @Override
            public String componentName() {
                return name;
            }

            @Override
            public int order() {
                return order;
            }

            @Override
            public ComponentHealth check() {
                return health;
            }
        };
    }
}
