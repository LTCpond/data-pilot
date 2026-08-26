package com.ltcpond.datapilot.api;

import com.ltcpond.datapilot.common.health.ComponentHealth;
import com.ltcpond.datapilot.common.health.SystemHealth;
import com.ltcpond.datapilot.core.health.SystemHealthService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class HealthControllerTest {

    @Test
    void shouldReturn200WhenEveryComponentIsUp() throws Exception {
        SystemHealthService service = mock(SystemHealthService.class);
        when(service.check()).thenReturn(health(ComponentHealth.up()));
        MockMvc mockMvc = standaloneSetup(new HealthController(service)).build();

        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.status").value("UP"))
                .andExpect(jsonPath("$.data.components.managementDatabase.status").value("UP"))
                .andExpect(jsonPath("$.data.components.businessDatabase.status").value("UP"))
                .andExpect(jsonPath("$.data.components.redis.status").value("UP"));
    }

    @Test
    void shouldReturn503WhenAComponentIsDown() throws Exception {
        SystemHealthService service = mock(SystemHealthService.class);
        when(service.check()).thenReturn(health(ComponentHealth.down()));
        MockMvc mockMvc = standaloneSetup(new HealthController(service)).build();

        mockMvc.perform(get("/api/health"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(503))
                .andExpect(jsonPath("$.message").value("service unavailable"))
                .andExpect(jsonPath("$.data.status").value("DOWN"))
                .andExpect(jsonPath("$.data.components.redis.status").value("DOWN"));
    }

    private SystemHealth health(ComponentHealth redisHealth) {
        Map<String, ComponentHealth> components = new LinkedHashMap<>();
        components.put("managementDatabase", ComponentHealth.up());
        components.put("businessDatabase", ComponentHealth.up());
        components.put("redis", redisHealth);
        return SystemHealth.from(components);
    }
}
