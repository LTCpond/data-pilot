package com.ltcpond.datapilot.api.async;

/** Redis不可用时异步结果无法可靠交付。 */
public class AsyncQueryUnavailableException extends RuntimeException {

    public AsyncQueryUnavailableException() {
        super("Async query infrastructure is unavailable");
    }
}
