package com.ltcpond.datapilot.api.async;

/** 异步提交确认及后续资源地址。 */
public record AsyncQueryAcceptedView(
        long queryId,
        String status,
        String eventsUrl,
        String resultUrl) {
}
