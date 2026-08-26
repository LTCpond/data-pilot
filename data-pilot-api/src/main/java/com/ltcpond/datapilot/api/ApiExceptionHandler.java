package com.ltcpond.datapilot.api;

import com.ltcpond.datapilot.common.api.ApiResponse;
import com.ltcpond.datapilot.common.api.ResponseCode;
import com.ltcpond.datapilot.common.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 将应用异常映射为稳定且脱敏的 HTTP 响应。 */
@Slf4j
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<ApiResponse<Void>> invalidRequest() {
        return error(ResponseCode.INVALID_REQUEST);
    }

    @ExceptionHandler(AppException.class)
    public ResponseEntity<ApiResponse<Void>> applicationException(AppException exception) {
        return ResponseEntity.status(exception.getHttpStatus())
                .body(ApiResponse.error(exception.getCode(), exception.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> unexpectedException(Exception exception) {
        // 异常消息可能包含连接信息或驱动细节，因此日志只记录异常类型。
        log.error("未处理异常类型: {}", exception.getClass().getName());
        return error(ResponseCode.INTERNAL_ERROR);
    }

    private ResponseEntity<ApiResponse<Void>> error(ResponseCode responseCode) {
        return ResponseEntity.status(HttpStatus.valueOf(responseCode.getHttpStatus()))
                .body(ApiResponse.error(responseCode.getCode(), responseCode.getMessage()));
    }
}
