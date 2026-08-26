package com.ltcpond.datapilot.common.exception;

import com.ltcpond.datapilot.common.api.ResponseCode;
import lombok.Getter;

import java.io.Serial;

/**
 * 统一应用异常。
 *
 * <p>{@code detailCode} 仅用于任务状态和内部诊断，不作为客户端提示返回，避免泄露底层错误。</p>
 */
@Getter
public class AppException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final ResponseCode responseCode;
    private final String detailCode;

    public AppException(ResponseCode responseCode) {
        this(responseCode, null, null);
    }

    public AppException(ResponseCode responseCode, String detailCode) {
        this(responseCode, detailCode, null);
    }

    public AppException(ResponseCode responseCode, String detailCode, Throwable cause) {
        super(responseCode.getMessage(), cause);
        this.responseCode = responseCode;
        this.detailCode = detailCode;
    }

    public int getCode() {
        return responseCode.getCode();
    }

    public int getHttpStatus() {
        return responseCode.getHttpStatus();
    }
}
