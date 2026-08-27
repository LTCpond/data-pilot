package com.ltcpond.datapilot.api;

import com.ltcpond.datapilot.api.controller.QueryController;
import com.ltcpond.datapilot.api.exception.ApiExceptionHandler;
import com.ltcpond.datapilot.common.api.ResponseCode;
import com.ltcpond.datapilot.common.exception.AppException;
import com.ltcpond.datapilot.core.query.QueryService;
import com.ltcpond.datapilot.core.query.QueryTaskView;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class QueryControllerTest {

    @Test
    void shouldReturnTaskDetailsAndHistory() throws Exception {
        QueryService service = mock(QueryService.class);
        QueryTaskView task = task();
        when(service.get(9L)).thenReturn(task);
        when(service.list(1L)).thenReturn(List.of(task));

        mockMvc(service).perform(get("/api/queries/9"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(9))
                .andExpect(jsonPath("$.data.status").value("SUCCEEDED"));

        mockMvc(service).perform(get("/api/datasources/1/queries"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].question").value("查询订单数量"));
    }

    @Test
    void shouldReturnNotFoundForMissingTask() throws Exception {
        QueryService service = mock(QueryService.class);
        when(service.get(99L)).thenThrow(new AppException(ResponseCode.QUERY_TASK_NOT_FOUND));

        mockMvc(service).perform(get("/api/queries/99"))
                .andExpect(status().isNotFound());
    }

    private MockMvc mockMvc(QueryService service) {
        return standaloneSetup(new QueryController(service))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    private QueryTaskView task() {
        LocalDateTime now = LocalDateTime.now();
        return new QueryTaskView(
                9L, 1L, "查询订单数量", "SUCCEEDED", null, List.of("orders"),
                "SELECT COUNT(*) FROM orders", null, null, 0, 1, 120L,
                null, null, now, now, null);
    }
}
