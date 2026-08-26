package com.ltcpond.datapilot.api;

import com.ltcpond.datapilot.common.api.ApiResponse;
import com.ltcpond.datapilot.common.health.SystemHealth;
import com.ltcpond.datapilot.core.health.SystemHealthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 对外提供 Data Pilot 依赖健康状态，不返回连接信息和底层异常。
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class HealthController {

    private final SystemHealthService healthService;

    @GetMapping("/health")
    public ResponseEntity<ApiResponse<SystemHealth>> health() {
        SystemHealth health = healthService.check();
        if (health.healthy()) {
            return ResponseEntity.ok(ApiResponse.success(health));
        }
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.unavailable(health));
    }
}
