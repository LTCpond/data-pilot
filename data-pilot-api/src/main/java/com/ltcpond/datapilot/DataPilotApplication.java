package com.ltcpond.datapilot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Data Pilot 应用启动入口。
 */
@SpringBootApplication
public class DataPilotApplication {

    /** 启动 Spring Boot 应用并触发组件扫描、配置绑定和自动装配。 */
    public static void main(String[] args) {
        SpringApplication.run(DataPilotApplication.class, args);
    }
}
