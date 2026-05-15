package com.grabmyseat.inventory.dto;

import com.grabmyseat.inventory.model.Event;
import com.grabmyseat.inventory.model.Seat;
import com.grabmyseat.inventory.model.SeatStatus;
import com.grabmyseat.inventory.model.Zone;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class EventResponseTest {

    @Test
    void zoneResponseCountsAvailableSeatsInSinglePass() {
        Event event = new Event("Concert", "Arena", Instant.now(), null, 1L);
        event.setArtworkUrl("https://images.example.com/concert.jpg");
        Zone zone = new Zone("VIP", 3, BigDecimal.TEN);
        zone.addSeat(new Seat("A", 1));
        zone.addSeat(new Seat("A", 2));
        zone.addSeat(new Seat("A", 3));
        event.addZone(zone);

        // mark one seat sold
        zone.getSeats().get(0).setStatus(SeatStatus.SOLD);

        EventResponse response = EventResponse.from(event);

        assertThat(response.artworkUrl()).isEqualTo("https://images.example.com/concert.jpg");
        assertThat(response.zones()).hasSize(1);
        EventResponse.ZoneResponse zr = response.zones().get(0);
        assertThat(zr.availableSeats()).isEqualTo(2L);
        assertThat(zr.seats()).hasSize(3);
    }
}
