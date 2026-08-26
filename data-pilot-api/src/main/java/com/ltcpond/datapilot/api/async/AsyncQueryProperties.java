package com.ltcpond.datapilot.api.async;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** 异步线程池、SSE和Redis临时结果的有界配置。 */
@Getter
@Setter
@ConfigurationProperties(prefix = "data-pilot.async-query")
public class AsyncQueryProperties {

    private int corePoolSize = 2;
    private int maxPoolSize = 4;
    private int queueCapacity = 50;
    private Duration resultTtl = Duration.ofMinutes(15);
    private Duration sseTimeout = Duration.ofMinutes(5);
}
