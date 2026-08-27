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

    /** 使用统一响应码创建应用异常，不携带内部诊断码。 */
    public AppException(ResponseCode responseCode) {
        this(responseCode, null, null);
    }

    /** 使用统一响应码和脱敏诊断码创建应用异常。 */
    public AppException(ResponseCode responseCode, String detailCode) {
        this(responseCode, detailCode, null);
    }

    /** 使用统一响应码、脱敏诊断码和原始异常创建应用异常。 */
    public AppException(ResponseCode responseCode, String detailCode, Throwable cause) {
        super(responseCode.getMessage(), cause);
        this.responseCode = responseCode;
        this.detailCode = detailCode;
    }

    /** 返回面向客户端和前端逻辑使用的稳定业务码。 */
    public int getCode() {
        return responseCode.getCode();
    }

    /** 返回该业务异常应映射到的 HTTP 状态码。 */
    public int getHttpStatus() {
        return responseCode.getHttpStatus();
    }
}
