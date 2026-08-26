package com.ltcpond.datapilot.ai;

/** AI 模型未启用或没有可用 ChatModel 时抛出。 */
public class AiModelUnavailableException extends RuntimeException {

    public AiModelUnavailableException() {
        super("AI model is unavailable");
    }
}
