package com.grabmyseat.inventory.web;

import com.grabmyseat.inventory.model.Reservation;
import com.grabmyseat.inventory.model.ReservationStatus;
import com.grabmyseat.inventory.model.Zone;
import com.grabmyseat.inventory.repository.ReservationRepository;
import com.grabmyseat.inventory.repository.ZoneRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/inventory/status")
public class StatusController {

    private final ReservationRepository reservationRepository;
    private final ZoneRepository zoneRepository;

    public StatusController(ReservationRepository reservationRepository, ZoneRepository zoneRepository) {
        this.reservationRepository = reservationRepository;
        this.zoneRepository = zoneRepository;
    }

    @GetMapping("/oversell")
    public ResponseEntity<OversellStatus> oversell() {
        List<Zone> zones = zoneRepository.findAll();
        List<Reservation> confirmed = reservationRepository.findByStatus(ReservationStatus.CONFIRMED);

        Map<Long, Integer> soldByZone = new HashMap<>();
        for (Reservation reservation : confirmed) {
            int seats = reservation.getSeatIds() == null ? 0 : reservation.getSeatIds().size();
            soldByZone.merge(reservation.getZoneId(), seats, Integer::sum);
        }

        long oversellCount = 0L;
        for (Zone zone : zones) {
            int sold = soldByZone.getOrDefault(zone.getId(), 0);
            int capacity = zone.getCapacity() == null ? 0 : zone.getCapacity();
            if (sold > capacity) {
                oversellCount += (sold - capacity);
            }
        }

        return ResponseEntity.ok(new OversellStatus(oversellCount));
    }

    public record OversellStatus(long oversellCount) {
    }
}
