package com.grabmyseat.waitingroom.service;

import com.grabmyseat.waitingroom.dto.SseEvent;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;

@Component
public class AdmissionScheduler {

    private final QueueService queueService;
    private final AdmissionService admissionService;
    private final SseEmitterService sseEmitterService;

    public AdmissionScheduler(QueueService queueService,
                              AdmissionService admissionService,
                              SseEmitterService sseEmitterService) {
        this.queueService = queueService;
        this.admissionService = admissionService;
        this.sseEmitterService = sseEmitterService;
    }

    @Scheduled(fixedDelayString = "1000")
    public void admit() {
        Set<String> eventIds = queueService.activeEventIds();
        if (eventIds == null || eventIds.isEmpty()) {
            return;
        }

        for (String eventIdStr : eventIds) {
            Long eventId;
            try {
                eventId = Long.valueOf(eventIdStr);
            } catch (NumberFormatException e) {
                continue;
            }

            int rate = admissionService.admissionRate();
            for (int i = 0; i < rate; i++) {
                Optional<String> admittedToken = admissionService.admitNext(eventId, queueService);
                if (admittedToken.isEmpty()) {
                    break;
                }

                String token = admittedToken.get();
                String permitToken = queueService.permitValue(token);
                if (permitToken != null) {
                    sseEmitterService.broadcast(eventId, new SseEvent("admitted", 0L, permitToken));
                }

                Long remaining = queueService.queueSize(eventId);
                if (remaining != null && remaining > 0) {
                    sseEmitterService.broadcast(eventId, new SseEvent("position", remaining, null));
                }
            }

            Long size = queueService.queueSize(eventId);
            if (size == null || size == 0L) {
                queueService.removeActiveEvent(eventId);
            }
        }
    }
}
