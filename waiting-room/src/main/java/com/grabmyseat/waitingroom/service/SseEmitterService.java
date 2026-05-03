package com.grabmyseat.waitingroom.service;

import com.grabmyseat.waitingroom.dto.SseEvent;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

@Service
public class SseEmitterService {

    private final Map<Long, CopyOnWriteArraySet<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(Long eventId) {
        SseEmitter emitter = new SseEmitter(0L);
        CopyOnWriteArraySet<SseEmitter> set = emitters.computeIfAbsent(eventId, k -> new CopyOnWriteArraySet<>());
        set.add(emitter);

        emitter.onCompletion(() -> set.remove(emitter));
        emitter.onTimeout(() -> set.remove(emitter));
        emitter.onError(e -> {
            emitter.completeWithError(e);
            set.remove(emitter);
        });

        return emitter;
    }

    public void broadcast(Long eventId, SseEvent event) {
        CopyOnWriteArraySet<SseEmitter> set = emitters.get(eventId);
        if (set == null) {
            return;
        }
        for (SseEmitter emitter : set) {
            try {
                emitter.send(SseEmitter.event().name(event.event()).data(event));
            } catch (IOException e) {
                emitter.completeWithError(e);
                set.remove(emitter);
            }
        }
    }

    public void removeEvent(Long eventId) {
        CopyOnWriteArraySet<SseEmitter> set = emitters.remove(eventId);
        if (set != null) {
            for (SseEmitter emitter : set) {
                emitter.complete();
            }
        }
    }

    public Set<Long> activeEventIds() {
        return emitters.keySet();
    }
}
