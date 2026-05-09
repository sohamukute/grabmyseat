package com.grabmyseat.inventory.service;

import com.grabmyseat.inventory.client.WaitingRoomClient;
import com.grabmyseat.inventory.model.Reservation;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class ReservationExpirySweeper {

    private final ReservationService reservationService;
    private final Optional<WaitingRoomClient> waitingRoomClient;
    private final Counter waitlistOffersSentCounter;

    public ReservationExpirySweeper(ReservationService reservationService,
                                    Optional<WaitingRoomClient> waitingRoomClient,
                                    MeterRegistry meterRegistry) {
        this.reservationService = reservationService;
        this.waitingRoomClient = waitingRoomClient;
        this.waitlistOffersSentCounter = Counter.builder("grabmyseat.waitlist.offers.sent")
                .description("Total number of waitlist offers sent")
                .register(meterRegistry);
    }

    @Scheduled(fixedDelayString = "${reservation.sweep.interval:60000}")
    public void sweep() {
        List<Reservation> released = reservationService.expireHeldReservations();
        waitingRoomClient.ifPresent(client -> {
            for (Reservation r : released) {
                client.notifyRelease(r.getEventId(), r.getZoneId(), r.getSeatIds().size());
                waitlistOffersSentCounter.increment();
            }
        });
    }
}
