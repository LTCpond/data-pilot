package com.ltcpond.datapilot.api;

import com.ltcpond.datapilot.ai.AiGenerationException;
import com.ltcpond.datapilot.ai.AiModelUnavailableException;
import com.ltcpond.datapilot.api.async.AsyncQueryQueueFullException;
import com.ltcpond.datapilot.api.async.AsyncQueryUnavailableException;
import com.ltcpond.datapilot.api.async.QueryResultExpiredException;
import com.ltcpond.datapilot.api.async.QueryResultNotAvailableException;
import com.ltcpond.datapilot.common.api.ApiResponse;
import com.ltcpond.datapilot.core.datasource.DatasourceConnectionException;
import com.ltcpond.datapilot.core.datasource.DatasourceNotFoundException;
import com.ltcpond.datapilot.core.datasource.DuplicateDatasourceException;
import com.ltcpond.datapilot.core.datasource.InvalidDatasourceException;
import com.ltcpond.datapilot.core.datasource.MetadataSyncException;
import com.ltcpond.datapilot.core.query.QueryFailedException;
import com.ltcpond.datapilot.core.query.QueryNotReadyException;
import com.ltcpond.datapilot.core.query.QueryRejectedException;
import com.ltcpond.datapilot.core.query.QueryTaskNotFoundException;
import com.ltcpond.datapilot.core.rag.RagIndexException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 将数据源异常映射为稳定且脱敏的 HTTP 响应。 */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<ApiResponse<Void>> invalidRequest() {
        return error(HttpStatus.BAD_REQUEST, "invalid request");
    }

    @ExceptionHandler(InvalidDatasourceException.class)
    public ResponseEntity<ApiResponse<Void>> invalidDatasource() {
        return error(HttpStatus.BAD_REQUEST, "invalid datasource configuration");
    }

    @ExceptionHandler(DatasourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> notFound() {
        return error(HttpStatus.NOT_FOUND, "datasource not found");
    }

    @ExceptionHandler(DuplicateDatasourceException.class)
    public ResponseEntity<ApiResponse<Void>> duplicate() {
        return error(HttpStatus.CONFLICT, "datasource name already exists");
    }

    @ExceptionHandler(DatasourceConnectionException.class)
    public ResponseEntity<ApiResponse<Void>> connectionFailure() {
        return error(HttpStatus.BAD_GATEWAY, "datasource is unreachable");
    }

    @ExceptionHandler(MetadataSyncException.class)
    public ResponseEntity<ApiResponse<Void>> syncFailure() {
        return error(HttpStatus.BAD_GATEWAY, "datasource metadata synchronization failed");
    }

    @ExceptionHandler(RagIndexException.class)
    public ResponseEntity<ApiResponse<Void>> ragIndexFailure() {
        return error(HttpStatus.BAD_GATEWAY, "schema vector indexing failed");
    }

    @ExceptionHandler(QueryNotReadyException.class)
    public ResponseEntity<ApiResponse<Void>> queryNotReady() {
        return error(HttpStatus.CONFLICT, "datasource schema is not ready");
    }

    @ExceptionHandler(QueryTaskNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> queryNotFound() {
        return error(HttpStatus.NOT_FOUND, "query task not found");
    }

    @ExceptionHandler(QueryRejectedException.class)
    public ResponseEntity<ApiResponse<Void>> queryRejected() {
        return error(HttpStatus.UNPROCESSABLE_ENTITY, "question or generated SQL was rejected");
    }

    @ExceptionHandler(AiModelUnavailableException.class)
    public ResponseEntity<ApiResponse<Void>> aiUnavailable() {
        return error(HttpStatus.SERVICE_UNAVAILABLE, "AI model is unavailable");
    }

    @ExceptionHandler(AiGenerationException.class)
    public ResponseEntity<ApiResponse<Void>> aiFailure() {
        return error(HttpStatus.BAD_GATEWAY, "AI SQL generation failed");
    }

    @ExceptionHandler(QueryFailedException.class)
    public ResponseEntity<ApiResponse<Void>> queryFailure() {
        return error(HttpStatus.BAD_GATEWAY, "read-only query execution failed");
    }

    @ExceptionHandler(AsyncQueryUnavailableException.class)
    public ResponseEntity<ApiResponse<Void>> asyncUnavailable() {
        return error(HttpStatus.SERVICE_UNAVAILABLE, "async query service is unavailable");
    }

    @ExceptionHandler(AsyncQueryQueueFullException.class)
    public ResponseEntity<ApiResponse<Void>> asyncQueueFull() {
        return error(HttpStatus.TOO_MANY_REQUESTS, "async query queue is full");
    }

    @ExceptionHandler(QueryResultExpiredException.class)
    public ResponseEntity<ApiResponse<Void>> resultExpired() {
        return error(HttpStatus.GONE, "query result expired");
    }

    @ExceptionHandler(QueryResultNotAvailableException.class)
    public ResponseEntity<ApiResponse<Void>> resultNotAvailable() {
        return error(HttpStatus.CONFLICT, "query result is not available");
    }

    private ResponseEntity<ApiResponse<Void>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(ApiResponse.error(status.value(), message));
    }
}
