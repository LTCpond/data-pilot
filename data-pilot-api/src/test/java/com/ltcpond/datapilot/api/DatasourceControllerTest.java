package com.ltcpond.datapilot.api;

import com.ltcpond.datapilot.common.api.ResponseCode;
import com.ltcpond.datapilot.common.exception.AppException;
import com.ltcpond.datapilot.core.datasource.DatasourceService;
import com.ltcpond.datapilot.core.datasource.DatasourceView;
import com.ltcpond.datapilot.core.rag.SchemaIndexService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class DatasourceControllerTest {

    @Test
    void shouldCreateWithoutReturningPassword() throws Exception {
        DatasourceService service = mock(DatasourceService.class);
        when(service.create(any())).thenReturn(view());
        MockMvc mockMvc = mockMvc(service);

        mockMvc.perform(post("/api/datasources")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"demo",
                                  "description":"demo database",
                                  "jdbcUrl":"jdbc:mysql://127.0.0.1:3307/ecommerce_demo",
                                  "username":"reader",
                                  "password":"test-secret"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("demo"))
                .andExpect(jsonPath("$.data.password").doesNotExist())
                .andExpect(jsonPath("$.data.encryptedPassword").doesNotExist())
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("test-secret"))));
    }

    @Test
    void shouldReturn400ForInvalidRequest() throws Exception {
        MockMvc mockMvc = mockMvc(mock(DatasourceService.class));

        mockMvc.perform(post("/api/datasources/test-connection")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jdbcUrl\":\"\",\"username\":\"\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("请求参数无效"));
    }

    @Test
    void shouldReturnSanitized502ForConnectionFailure() throws Exception {
        DatasourceService service = mock(DatasourceService.class);
        when(service.testConnection(any())).thenThrow(new AppException(ResponseCode.DATASOURCE_UNREACHABLE));
        MockMvc mockMvc = mockMvc(service);

        mockMvc.perform(post("/api/datasources/test-connection")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"jdbcUrl":"jdbc:mysql://secret-host/db","username":"reader","password":"secret"}
                                """))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message").value("数据源无法连接"))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("secret-host"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("secret"))));
    }

    @Test
    void shouldReturn404ForMissingDatasource() throws Exception {
        DatasourceService service = mock(DatasourceService.class);
        when(service.get(99L)).thenThrow(new AppException(ResponseCode.DATASOURCE_NOT_FOUND));

        mockMvc(service).perform(get("/api/datasources/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("数据源不存在"));
    }

    @Test
    void shouldReturn409ForDuplicateName() throws Exception {
        DatasourceService service = mock(DatasourceService.class);
        when(service.create(any())).thenThrow(new AppException(ResponseCode.DUPLICATE_DATASOURCE_NAME));

        mockMvc(service).perform(post("/api/datasources")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"demo",
                                  "jdbcUrl":"jdbc:mysql://127.0.0.1:3307/ecommerce_demo",
                                  "username":"reader",
                                  "password":"test-secret"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("数据源名称已存在"));
    }

    @Test
    void shouldReturnSanitized502WhenRagIndexFails() throws Exception {
        DatasourceService service = mock(DatasourceService.class);
        SchemaIndexService indexService = mock(SchemaIndexService.class);
        when(indexService.rebuild(1L)).thenThrow(new AppException(ResponseCode.SCHEMA_VECTOR_INDEX_FAILED));
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        MockMvc mockMvc = standaloneSetup(new DatasourceController(service, indexService))
                .setControllerAdvice(new ApiExceptionHandler())
                .setValidator(validator)
                .build();

        mockMvc.perform(post("/api/datasources/1/rag-index"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value(50203))
                .andExpect(jsonPath("$.message").value("Schema 向量索引失败"));
    }

    private MockMvc mockMvc(DatasourceService service) {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        return standaloneSetup(new DatasourceController(service, mock(SchemaIndexService.class)))
                .setControllerAdvice(new ApiExceptionHandler())
                .setValidator(validator)
                .build();
    }

    private DatasourceView view() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 16, 10, 0);
        return new DatasourceView(
                1L,
                "demo",
                "demo database",
                "MYSQL",
                "jdbc:mysql://127.0.0.1:3307/ecommerce_demo",
                "reader",
                "CONNECTED",
                null,
                "PENDING",
                null,
                0,
                null,
                now,
                now);
    }
}
