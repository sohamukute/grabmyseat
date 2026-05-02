package com.grabmyseat.live;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

@Component
public class SeatStream {

    private final JdbcClient jdbc;
    private final Map<Long, Set<SseEmitter>> viewers = new ConcurrentHashMap<>();
    private final Map<Long, String> seen = new ConcurrentHashMap<>();
    private int ticks;

    public SeatStream(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public SseEmitter watch(long eventId) {
        SseEmitter emitter = new SseEmitter(1_800_000L);
        viewers.computeIfAbsent(eventId, id -> new CopyOnWriteArraySet<>()).add(emitter);
        Runnable leave = () -> viewers.getOrDefault(eventId, Set.of()).remove(emitter);
        emitter.onCompletion(leave);
        emitter.onTimeout(leave);
        emitter.onError(failed -> leave.run());
        String current = versions(List.of(eventId)).getOrDefault(eventId, "");
        seen.putIfAbsent(eventId, current);
        send(emitter, eventId, current);
        return emitter;
    }

    @Scheduled(fixedDelay = 1_000)
    public void push() {
        viewers.values().removeIf(Set::isEmpty);
        if (viewers.isEmpty()) {
            return;
        }
        boolean ping = ++ticks % 25 == 0;
        versions(List.copyOf(viewers.keySet())).forEach((eventId, current) -> {
            boolean changed = !current.equals(seen.put(eventId, current));
            for (SseEmitter emitter : viewers.getOrDefault(eventId, Set.of())) {
                if (changed) {
                    send(emitter, eventId, current);
                } else if (ping) {
                    try {
                        emitter.send(SseEmitter.event().comment("ping"));
                    } catch (IOException | IllegalStateException gone) {
                        viewers.get(eventId).remove(emitter);
                    }
                }
            }
        });
    }

    private void send(SseEmitter emitter, long eventId, String current) {
        try {
            emitter.send(SseEmitter.event().name("seats").data(current));
        } catch (IOException | IllegalStateException gone) {
            viewers.getOrDefault(eventId, Set.of()).remove(emitter);
        }
    }

    private Map<Long, String> versions(List<Long> eventIds) {
        Map<Long, String> result = new ConcurrentHashMap<>();
        jdbc.sql("""
                        SELECT event_id, string_agg(area_id || ':' || version, ',' ORDER BY area_id) AS versions
                        FROM event_area WHERE event_id IN (:events) GROUP BY event_id
                        """)
                .param("events", eventIds)
                .query((rs, row) -> result.put(rs.getLong("event_id"), rs.getString("versions")))
                .list();
        return result;
    }
}
