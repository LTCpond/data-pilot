package com.ltcpond.datapilot.ai;

/** 模型调用或结构化响应转换失败；异常信息刻意保持稳定且脱敏。 */
public class AiGenerationException extends RuntimeException {

    private final String errorCode;

    public AiGenerationException(String errorCode) {
        super("AI SQL generation failed");
        this.errorCode = errorCode;
    }

    AiGenerationException(String errorCode, Throwable cause) {
        super("AI SQL generation failed", cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
