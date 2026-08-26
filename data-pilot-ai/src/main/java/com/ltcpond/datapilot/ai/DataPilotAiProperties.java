package com.ltcpond.datapilot.ai;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

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
}
