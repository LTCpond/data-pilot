package com.ltcpond.datapilot.api;

import com.ltcpond.datapilot.api.async.AsyncQueryAcceptedView;
import com.ltcpond.datapilot.api.async.AsyncQueryCoordinator;
import com.ltcpond.datapilot.api.async.AsyncQueryResultView;
import com.ltcpond.datapilot.api.async.QuerySseService;
import com.ltcpond.datapilot.common.api.ResponseCode;
import com.ltcpond.datapilot.common.exception.AppException;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class AsyncQueryControllerTest {

    @Test
    void shouldAcceptTaskAndReturnResourceUrls() throws Exception {
        AsyncQueryCoordinator coordinator = mock(AsyncQueryCoordinator.class);
        when(coordinator.submit(any())).thenReturn(new AsyncQueryAcceptedView(
                9L, "CREATED", "/api/queries/9/events", "/api/queries/9/result"));

        mockMvc(coordinator).perform(post("/api/datasources/1/queries/async")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"查询订单数量\",\"maxRows\":100}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.queryId").value(9))
                .andExpect(jsonPath("$.data.eventsUrl").value("/api/queries/9/events"));
    }

    @Test
    void shouldReturnAcceptedWhileResultIsRunningAndConflictWhenFailed() throws Exception {
        AsyncQueryCoordinator coordinator = mock(AsyncQueryCoordinator.class);
        when(coordinator.result(9L)).thenReturn(new AsyncQueryResultView(
                9L, "SQL_GENERATING", null, null, null));
        when(coordinator.result(10L)).thenReturn(new AsyncQueryResultView(
                10L, "FAILED", "AI_MODEL_UNAVAILABLE", null, null));

        mockMvc(coordinator).perform(get("/api/queries/9/result"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.status").value("SQL_GENERATING"));
        mockMvc(coordinator).perform(get("/api/queries/10/result"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.data.errorCode").value("AI_MODEL_UNAVAILABLE"));
    }

    @Test
    void shouldReturnGoneAfterRedisResultExpires() throws Exception {
        AsyncQueryCoordinator coordinator = mock(AsyncQueryCoordinator.class);
        when(coordinator.result(11L)).thenThrow(new AppException(ResponseCode.QUERY_RESULT_EXPIRED));

        mockMvc(coordinator).perform(get("/api/queries/11/result"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value(41001))
                .andExpect(jsonPath("$.message").value("查询结果已过期"));
    }

    private org.springframework.test.web.servlet.MockMvc mockMvc(AsyncQueryCoordinator coordinator) {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        return standaloneSetup(new AsyncQueryController(coordinator, mock(QuerySseService.class)))
                .setControllerAdvice(new ApiExceptionHandler())
                .setValidator(validator)
                .build();
    }
}
