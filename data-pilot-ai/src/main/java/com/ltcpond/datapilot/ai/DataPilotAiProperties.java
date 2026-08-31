package com.ltcpond.datapilot.ai;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** Data Pilot 自身的工作流限制，不包含模型密钥。 */
@Getter
@Setter
@ConfigurationProperties("data-pilot.ai")
public class DataPilotAiProperties {

    private boolean enabled;
    private String model;
    private int maximumRepairAttempts = 2;
    private int defaultMaxRows = 100;
    private int absoluteMaxRows = 200;
    private int maximumAgentTurns = 8;
    private int maximumTotalToolFailures = 4;
    private int maximumSameFailure = 3;
    private int toolObservationMaxRows = 20;
    private int toolObservationMaxChars = 8_000;
    private Duration agentTimeout = Duration.ofSeconds(120);
}
