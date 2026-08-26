package com.ltcpond.datapilot.api;

import com.ltcpond.datapilot.common.api.ResponseCode;
import com.ltcpond.datapilot.common.exception.AppException;
import com.ltcpond.datapilot.core.query.QueryResultView;
import com.ltcpond.datapilot.core.query.QueryService;
import com.ltcpond.datapilot.core.query.RetrievalView;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class QueryControllerTest {

    @Test
    void shouldReturnGeneratedSqlAndRows() throws Exception {
        QueryService service = mock(QueryService.class);
        when(service.execute(any())).thenReturn(result());

        mockMvc(service).perform(post("/api/datasources/1/queries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question":"查询订单数量","maxRows":100}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.sql").value("SELECT COUNT(*) AS order_count FROM orders LIMIT 100"))
                .andExpect(jsonPath("$.data.rows[0].order_count").value(60));
    }

    @Test
    void shouldValidateQuestionAndRowLimit() throws Exception {
        MockMvc mockMvc = mockMvc(mock(QueryService.class));

        mockMvc.perform(post("/api/datasources/1/queries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"\",\"maxRows\":201}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("请求参数无效"));
    }

    @Test
    void shouldReturnStableWorkflowErrors() throws Exception {
        QueryService notReady = mock(QueryService.class);
        when(notReady.execute(any())).thenThrow(new AppException(ResponseCode.DATASOURCE_SCHEMA_NOT_READY));
        mockMvc(notReady).perform(validRequest()).andExpect(status().isConflict());

        QueryService rejected = mock(QueryService.class);
        when(rejected.execute(any())).thenThrow(new AppException(ResponseCode.QUERY_REJECTED));
        mockMvc(rejected).perform(validRequest()).andExpect(status().isUnprocessableEntity());

        QueryService unavailable = mock(QueryService.class);
        when(unavailable.execute(any())).thenThrow(new AppException(ResponseCode.AI_MODEL_UNAVAILABLE));
        mockMvc(unavailable).perform(validRequest()).andExpect(status().isServiceUnavailable());
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder validRequest() {
        return post("/api/datasources/1/queries")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"查询订单数量\"}");
    }

    private MockMvc mockMvc(QueryService service) {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        return standaloneSetup(new QueryController(service))
                .setControllerAdvice(new ApiExceptionHandler())
                .setValidator(validator)
                .build();
    }

    private QueryResultView result() {
        return new QueryResultView(
                1L,
                "SUCCEEDED",
                "统计订单数量",
                List.of("orders"),
                "SELECT COUNT(*) AS order_count FROM orders LIMIT 100",
                "统计订单表总行数",
                new BigDecimal("0.95"),
                List.of("order_count"),
                List.of(Map.of("order_count", 60)),
                1,
                120L,
                new RetrievalView("FULL_SCHEMA", false, 5, 5,
                        List.of("users", "shops", "products", "orders", "order_items"), 1L));
    }
}
