package com.grabmyseat.inventory.web;

import com.grabmyseat.inventory.model.Reservation;
import com.grabmyseat.inventory.model.ReservationStatus;
import com.grabmyseat.inventory.model.Zone;
import com.grabmyseat.inventory.repository.ReservationRepository;
import com.grabmyseat.inventory.repository.ZoneRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StatusController.class)
@AutoConfigureMockMvc(addFilters = false)
class StatusControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ReservationRepository reservationRepository;

    @MockBean
    private ZoneRepository zoneRepository;

    @Test
    void oversell_noConfirmedReservations_returnsZero() throws Exception {
        when(zoneRepository.findAll()).thenReturn(List.of(
                zone(1L, 100),
                zone(2L, 50)
        ));
        when(reservationRepository.findByStatus(ReservationStatus.CONFIRMED)).thenReturn(List.of());

        mockMvc.perform(get("/api/inventory/status/oversell"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.oversellCount").value(0));
    }

    @Test
    void oversell_soldEqualsCapacity_returnsZero() throws Exception {
        Zone zone = zone(1L, 10);
        when(zoneRepository.findAll()).thenReturn(List.of(zone));
        when(reservationRepository.findByStatus(ReservationStatus.CONFIRMED)).thenReturn(List.of(
                reservation(1L, zone.getId(), List.of(101L, 102L, 103L, 104L, 105L)),
                reservation(2L, zone.getId(), List.of(106L, 107L, 108L, 109L, 110L))
        ));

        mockMvc.perform(get("/api/inventory/status/oversell"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.oversellCount").value(0));
    }

    @Test
    void oversell_soldExceedsCapacity_returnsDifference() throws Exception {
        Zone zone = zone(1L, 5);
        when(zoneRepository.findAll()).thenReturn(List.of(zone));
        when(reservationRepository.findByStatus(ReservationStatus.CONFIRMED)).thenReturn(List.of(
                reservation(1L, zone.getId(), List.of(101L, 102L, 103L)),
                reservation(2L, zone.getId(), List.of(104L, 105L, 106L, 107L, 108L, 109L, 110L))
        ));

        mockMvc.perform(get("/api/inventory/status/oversell"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.oversellCount").value(5));
    }

    private Zone zone(Long id, int capacity) {
        Zone z = new Zone("GA", capacity, new BigDecimal("10.00"));
        try {
            java.lang.reflect.Field idField = Zone.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(z, id);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return z;
    }

    private Reservation reservation(Long id, Long zoneId, List<Long> seatIds) {
        Reservation r = new Reservation(42L, 1L, zoneId, seatIds, Instant.now().plusSeconds(300), "tok-" + id, java.math.BigDecimal.ZERO);
        r.setStatus(ReservationStatus.CONFIRMED);
        try {
            java.lang.reflect.Field idField = Reservation.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(r, id);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return r;
    }
}
