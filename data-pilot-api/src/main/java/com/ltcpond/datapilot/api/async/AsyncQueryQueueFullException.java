package com.ltcpond.datapilot.api.async;

/** 有界线程池已满，拒绝继续增加模型调用和查询压力。 */
public class AsyncQueryQueueFullException extends RuntimeException {

    public AsyncQueryQueueFullException() {
        super("Async query queue is full");
    }
}
