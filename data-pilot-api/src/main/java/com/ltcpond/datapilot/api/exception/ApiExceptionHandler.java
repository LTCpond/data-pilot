package com.ltcpond.datapilot.api.exception;

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
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** 将应用异常映射为稳定且脱敏的 HTTP 响应。 */
@Slf4j
@RestControllerAdvice
public class ApiExceptionHandler {

    /** 将请求体格式错误和参数校验失败统一映射为 400。 */
    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<ApiResponse<Void>> invalidRequest() {
        return error(ResponseCode.INVALID_REQUEST);
    }

    /** 将不存在的控制器或静态资源统一映射为 404。 */
    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<ApiResponse<Void>> resourceNotFound() {
        return error(ResponseCode.RESOURCE_NOT_FOUND);
    }

    /** 将业务异常中的稳定响应码和 HTTP 状态透传给客户端。 */
    @ExceptionHandler(AppException.class)
    public ResponseEntity<ApiResponse<Void>> applicationException(AppException exception) {
        return ResponseEntity.status(exception.getHttpStatus())
                .body(ApiResponse.error(exception.getCode(), exception.getMessage()));
    }

    /** 捕获未预期异常并返回脱敏的内部错误响应。 */
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
