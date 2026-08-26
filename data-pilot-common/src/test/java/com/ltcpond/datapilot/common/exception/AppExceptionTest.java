package com.ltcpond.datapilot.common.exception;

import com.ltcpond.datapilot.common.api.ResponseCode;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class AppExceptionTest {

    @Test
    void shouldExposeStableResponseAndKeepDiagnosticCodeSeparate() {
        RuntimeException cause = new RuntimeException("底层敏感错误");

        AppException exception = new AppException(
                ResponseCode.AI_SQL_GENERATION_FAILED, "AI_REQUEST_TIMEOUT", cause);

        assertThat(exception.getCode()).isEqualTo(50204);
        assertThat(exception.getHttpStatus()).isEqualTo(502);
        assertThat(exception.getMessage()).isEqualTo("AI SQL 生成失败");
        assertThat(exception.getDetailCode()).isEqualTo("AI_REQUEST_TIMEOUT");
        assertThat(exception.getMessage()).doesNotContain("底层敏感错误");
        assertThat(exception.getCause()).isSameAs(cause);
    }

    @Test
    void shouldKeepEveryBusinessCodeUnique() {
        assertThat(Arrays.stream(ResponseCode.values()).map(ResponseCode::getCode))
                .doesNotHaveDuplicates();
    }
}
