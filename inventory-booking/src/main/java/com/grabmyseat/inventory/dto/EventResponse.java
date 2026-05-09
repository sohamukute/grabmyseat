package com.grabmyseat.inventory.dto;

import com.grabmyseat.inventory.model.Event;
import com.grabmyseat.inventory.model.SaleType;
import com.grabmyseat.inventory.model.SeatStatus;
import com.grabmyseat.inventory.model.Zone;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record EventResponse(
        Long id,
        String name,
        String venue,
        String artworkUrl,
        Instant startsAt,
        Instant endsAt,
        Instant queueOpensAt,
        Instant saleStartsAt,
        Instant saleEndsAt,
        SaleType saleType,
        Long organizerId,
        Instant createdAt,
        List<ZoneResponse> zones
) {
    public static EventResponse from(Event event) {
        return new EventResponse(
                event.getId(),
                event.getName(),
                event.getVenue(),
                event.getArtworkUrl(),
                event.getStartsAt(),
                event.getEndsAt(),
                event.getQueueOpensAt(),
                event.getSaleStartsAt(),
                event.getSaleEndsAt(),
                event.effectiveSaleType(Instant.now()),
                event.getOrganizerId(),
                event.getCreatedAt(),
                event.getZones().stream().map(ZoneResponse::from).toList()
        );
    }

    public record ZoneResponse(
            Long id,
            String name,
            String type,
            Integer capacity,
            BigDecimal price,
            Long availableSeats,
            List<SeatResponse> seats
    ) {
        public static ZoneResponse from(Zone zone) {
            List<SeatResponse> seats = zone.getSeats().stream().map(SeatResponse::from).toList();
            String type = "General Admission".equals(zone.getName()) ? "STANDING" : "SEATED";
            long available = seats.stream().filter(s -> s.status().equals(SeatStatus.AVAILABLE.name())).count();
            return new ZoneResponse(
                    zone.getId(),
                    zone.getName(),
                    type,
                    zone.getCapacity(),
                    zone.getPrice(),
                    available,
                    seats
            );
        }
    }

    public record SeatResponse(Long id, String rowLabel, Integer number, String status) {
        public static SeatResponse from(com.grabmyseat.inventory.model.Seat seat) {
            return new SeatResponse(seat.getId(), seat.getRowLabel(), seat.getNumber(), seat.getStatus().name());
        }
    }
}
