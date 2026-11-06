package com.examgrid.backend.scheduling;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Fans run progress out to Server-Sent Event subscribers. The latest event per run is kept so
 * a browser that connects (or reconnects) mid-run immediately sees the current state instead
 * of an empty bar. Terminal events complete every emitter of that run.
 *
 * <p>SSE rather than WebSockets: updates flow one way (server → browser), SSE works over plain
 * HTTP through Nginx and corporate proxies, and the browser reconnects automatically.
 */
@Component
public class RunProgressBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(RunProgressBroadcaster.class);
    static final Duration EMITTER_TIMEOUT = Duration.ofMinutes(30);

    private final Map<Long, List<SseEmitter>> emitters = new ConcurrentHashMap<>();
    private final Map<Long, RunEvent> latest = new ConcurrentHashMap<>();

    /** Subscribes to a run; {@code current} is sent first (from memory if fresher). */
    public SseEmitter subscribe(long runId, RunEvent current) {
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT.toMillis());
        RunEvent first = latest.getOrDefault(runId, current);
        if (current.terminal()) {
            first = current;
        }
        if (!send(emitter, first)) {
            return emitter;
        }
        if (first.terminal()) {
            emitter.complete();
            return emitter;
        }
        List<SseEmitter> list = emitters.computeIfAbsent(runId, id -> new CopyOnWriteArrayList<>());
        list.add(emitter);
        Runnable remove = () -> list.remove(emitter);
        emitter.onCompletion(remove);
        emitter.onTimeout(remove);
        emitter.onError(e -> remove.run());
        return emitter;
    }

    public void publish(RunEvent event) {
        latest.put(event.runId(), event);
        List<SseEmitter> list = emitters.getOrDefault(event.runId(), List.of());
        for (SseEmitter emitter : list) {
            if (!send(emitter, event)) {
                list.remove(emitter);
            } else if (event.terminal()) {
                emitter.complete();
            }
        }
        if (event.terminal()) {
            emitters.remove(event.runId());
            latest.remove(event.runId());
        }
    }

    public int subscriberCount(long runId) {
        return emitters.getOrDefault(runId, List.of()).size();
    }

    /** Comment lines keep idle connections alive through proxies that close quiet sockets. */
    @Scheduled(fixedRate = 15_000)
    void heartbeat() {
        emitters.forEach((runId, list) -> list.forEach(emitter -> {
            try {
                emitter.send(SseEmitter.event().comment("keep-alive"));
            } catch (IOException | IllegalStateException e) {
                list.remove(emitter);
            }
        }));
    }

    private static boolean send(SseEmitter emitter, RunEvent event) {
        try {
            emitter.send(SseEmitter.event()
                    .name(event.terminal() ? "status" : "progress")
                    .id(event.runId() + "-" + event.pct())
                    .data(event, MediaType.APPLICATION_JSON));
            return true;
        } catch (IOException | IllegalStateException e) {
            log.debug("SSE subscriber of run {} went away: {}", event.runId(), e.getMessage());
            emitter.completeWithError(e);
            return false;
        }
    }
}
