package com.ltcpond.datapilot.api.async;

import com.ltcpond.datapilot.core.query.QueryService;
import com.ltcpond.datapilot.core.query.QueryStatus;
import com.ltcpond.datapilot.core.query.QueryStatusEvent;
import com.ltcpond.datapilot.core.query.QueryTaskView;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** 管理当前实例上的SSE连接；重连时以MySQL任务快照恢复。 */
@Service
@RequiredArgsConstructor
public class QuerySseService {

    private final QueryService queryService;
    private final AsyncQueryProperties properties;
    private final Map<Long, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public SseEmitter connect(long queryId) {
        QueryTaskView task = queryService.get(queryId);
        SseEmitter emitter = new SseEmitter(properties.getSseTimeout().toMillis());
        emitters.computeIfAbsent(queryId, ignored -> new CopyOnWriteArrayList<>()).add(emitter);
        emitter.onCompletion(() -> remove(queryId, emitter));
        emitter.onTimeout(() -> remove(queryId, emitter));
        emitter.onError(ignored -> remove(queryId, emitter));
        send(queryId, emitter, event(task), "query-snapshot");
        if (terminal(task.status())) {
            emitter.complete();
        }
        return emitter;
    }

    public void publish(QueryStatusEvent event) {
        String name = terminal(event.status()) ? "query-completed" : "query-status";
        for (SseEmitter emitter : emitters.getOrDefault(event.queryId(), new CopyOnWriteArrayList<>())) {
            send(event.queryId(), emitter, event, name);
            if (terminal(event.status())) {
                emitter.complete();
            }
        }
    }

    @Scheduled(fixedDelay = 15_000)
    void heartbeat() {
        for (Map.Entry<Long, CopyOnWriteArrayList<SseEmitter>> entry : emitters.entrySet()) {
            for (SseEmitter emitter : entry.getValue()) {
                send(entry.getKey(), emitter, Map.of("time", LocalDateTime.now()), "heartbeat");
            }
        }
    }

    private QueryStatusEvent event(QueryTaskView task) {
        return new QueryStatusEvent(
                task.id(), task.status(), task.errorCode(),
                QueryStatus.SUCCEEDED.name().equals(task.status()), LocalDateTime.now());
    }

    private void send(long queryId, SseEmitter emitter, Object data, String name) {
        try {
            emitter.send(SseEmitter.event().name(name).data(data));
        } catch (IOException | IllegalStateException exception) {
            remove(queryId, emitter);
        }
    }

    private void remove(long queryId, SseEmitter emitter) {
        List<SseEmitter> taskEmitters = emitters.get(queryId);
        if (taskEmitters == null) {
            return;
        }
        taskEmitters.remove(emitter);
        if (taskEmitters.isEmpty()) {
            emitters.remove(queryId);
        }
    }

    private boolean terminal(String status) {
        return QueryStatus.SUCCEEDED.name().equals(status)
                || QueryStatus.FAILED.name().equals(status)
                || QueryStatus.CANCELLED.name().equals(status);
    }
}
