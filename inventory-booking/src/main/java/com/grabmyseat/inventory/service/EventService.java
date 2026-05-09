package com.grabmyseat.inventory.service;

import com.grabmyseat.inventory.dto.CreateEventRequest;
import com.grabmyseat.inventory.dto.EventLayoutRequest;
import com.grabmyseat.inventory.dto.CreateZoneRequest;
import com.grabmyseat.inventory.dto.EventResponse;
import com.grabmyseat.inventory.dto.SaleAccessResponse;
import com.grabmyseat.inventory.model.Event;
import com.grabmyseat.inventory.model.SaleType;
import com.grabmyseat.inventory.model.Zone;
import com.grabmyseat.inventory.model.Seat;
import com.grabmyseat.inventory.repository.EventRepository;
import com.grabmyseat.inventory.repository.SeatRepository;
import com.grabmyseat.inventory.repository.ZoneRepository;
import com.grabmyseat.inventory.service.CapacityService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class EventService {

    private final EventRepository eventRepository;
    private final ZoneRepository zoneRepository;
    private final SeatRepository seatRepository;
    private final CapacityService capacityService;

    public EventService(EventRepository eventRepository, ZoneRepository zoneRepository, SeatRepository seatRepository, CapacityService capacityService) {
        this.capacityService = capacityService;
        this.eventRepository = eventRepository;
        this.zoneRepository = zoneRepository;
        this.seatRepository = seatRepository;
    }

    @Transactional
    public EventResponse createEvent(Long organizerId, CreateEventRequest request) {
        validateEventRequest(request);
        Instant queueOpensAt = request.saleType() == SaleType.FLASH
                ? request.queueOpensAt()
                : request.saleStartsAt();
        Event event = new Event(request.name(), request.venue(), request.artworkUrl().trim(),
                request.startsAt(), request.endsAt(), queueOpensAt,
                request.saleStartsAt(), request.saleEndsAt(), request.saleType(), organizerId);
        addFixedLayout(event, request.layout());
        Event saved = eventRepository.save(event);
        for (Zone zone : saved.getZones()) {
            capacityService.initialize(zone.getId(), zone.getCapacity());
        }
        return EventResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<EventResponse> listEvents() {
        return eventRepository.findAllByOrderByStartsAtDesc().stream()
                .map(EventResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<EventResponse> listEventsByOrganizer(Long organizerId) {
        return eventRepository.findByOrganizerIdOrderByStartsAtDesc(organizerId).stream()
                .map(EventResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public EventResponse getEvent(Long id) {
        Event event = eventRepository.findByIdWithZonesAndSeats(id)
                .orElseThrow(() -> new EntityNotFoundException("event not found"));
        return EventResponse.from(event);
    }

    @Transactional
    public EventResponse updateEvent(Long id, Long organizerId, CreateEventRequest request) {
        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("event not found"));
        if (!event.getOrganizerId().equals(organizerId)) {
            throw new SecurityException("not the organizer of this event");
        }
        event.setName(request.name());
        event.setVenue(request.venue());
        event.setArtworkUrl(request.artworkUrl().trim());
        event.setStartsAt(request.startsAt());
        event.setEndsAt(request.endsAt());
        validateEventRequest(request);
        event.setQueueOpensAt(request.saleType() == SaleType.FLASH
                ? request.queueOpensAt()
                : request.saleStartsAt());
        event.setSaleStartsAt(request.saleStartsAt());
        event.setSaleEndsAt(request.saleEndsAt());
        event.setSaleType(request.saleType());
        return EventResponse.from(event);
    }

    @Transactional(readOnly = true)
    public EventResponse getEventForOrganizer(Long id, Long organizerId) {
        Event event = eventRepository.findByIdWithZonesAndSeats(id)
                .orElseThrow(() -> new EntityNotFoundException("event not found"));
        if (!event.getOrganizerId().equals(organizerId)) {
            throw new SecurityException("not the organizer of this event");
        }
        return EventResponse.from(event);
    }

    @Transactional(readOnly = true)
    public EventResponse.ZoneResponse getZone(Long eventId, Long zoneId) {
        Zone zone = zoneRepository.findByIdWithSeats(zoneId)
                .orElseThrow(() -> new EntityNotFoundException("zone not found"));
        if (!zone.getEvent().getId().equals(eventId)) {
            throw new EntityNotFoundException("zone not found");
        }
        return EventResponse.ZoneResponse.from(zone);
    }

    @Transactional(readOnly = true)
    public SaleAccessResponse saleAccess(Long eventId) {
        Event event = eventRepository.findByIdWithZonesAndSeats(eventId)
                .orElseThrow(() -> new EntityNotFoundException("event not found"));
        Instant now = Instant.now();
        long available = event.getZones().stream()
                .mapToLong(zone -> zone.getSeats().stream()
                        .filter(seat -> seat.getStatus() == com.grabmyseat.inventory.model.SeatStatus.AVAILABLE)
                        .count())
                .sum();
        boolean beforeEvent = now.isBefore(event.getStartsAt());
        boolean interestWindow = beforeEvent && now.isBefore(event.getStartsAt().minus(Duration.ofDays(3)));
        boolean queueOpen = !now.isBefore(event.getQueueOpensAt()) && beforeEvent;
        boolean canJoin = queueOpen && available > 0;
        String status;
        if (!beforeEvent) status = "EVENT_STARTED";
        else if (available == 0) status = interestWindow ? "SOLD_OUT_INTEREST_OPEN" : "SOLD_OUT";
        else if (now.isBefore(event.getQueueOpensAt())) status = "QUEUE_OPENS_SOON";
        else if (now.isBefore(event.getSaleStartsAt())) status = "QUEUE_OPEN";
        else status = "ON_SALE";
        return new SaleAccessResponse(event.getId(), event.effectiveSaleType(now), event.getQueueOpensAt(),
                event.getSaleStartsAt(), event.getSaleEndsAt(), available, canJoin,
                available == 0 && interestWindow, status);
    }

    private void addFixedLayout(Event event, EventLayoutRequest layout) {
        event.addZone(seatedZone("General Admission", "GA", layout.generalAdmissionCapacity(), layout.generalAdmissionPrice()));
        event.addZone(seatedZone("Left Premium", "L", layout.leftPremiumCapacity(), layout.leftPremiumPrice()));
        event.addZone(seatedZone("Right Premium", "R", layout.rightPremiumCapacity(), layout.rightPremiumPrice()));
    }

    private Zone seatedZone(String name, String rowLabel, int capacity, BigDecimal price) {
        Zone zone = new Zone(name, capacity, price);
        for (int number = 1; number <= capacity; number++) {
            zone.addSeat(new Seat(rowLabel, number));
        }
        return zone;
    }

    private void validateEventRequest(CreateEventRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        validateLayout(request, errors);
        validateSaleSchedule(request, errors);
        if (!errors.isEmpty()) {
            throw new EventValidationException(errors);
        }
    }

    private void validateLayout(CreateEventRequest request, Map<String, String> errors) {
        if (request.zones() != null) {
            for (int index = 0; index < request.zones().size(); index++) {
                String type = request.zones().get(index).type();
                if (type != null && !type.equals("STANDING") && !type.equals("SEATED")) {
                    errors.put("zones[" + index + "].type", "Use STANDING or SEATED.");
                }
            }
        }
        EventLayoutRequest layout = request.layout();
        if (layout == null) {
            errors.put("layout", "Configure General Admission, Left Premium, and Right Premium.");
            return;
        }
        requireCapacity(layout.generalAdmissionCapacity(), "layout.generalAdmissionCapacity", errors);
        requirePrice(layout.generalAdmissionPrice(), "layout.generalAdmissionPrice", errors);
        requireCapacity(layout.leftPremiumCapacity(), "layout.leftPremiumCapacity", errors);
        requirePrice(layout.leftPremiumPrice(), "layout.leftPremiumPrice", errors);
        requireCapacity(layout.rightPremiumCapacity(), "layout.rightPremiumCapacity", errors);
        requirePrice(layout.rightPremiumPrice(), "layout.rightPremiumPrice", errors);
        validateSubmittedZoneTypes(request.zones(), errors);
    }

    private void validateSubmittedZoneTypes(List<CreateZoneRequest> zones, Map<String, String> errors) {
        if (zones == null || zones.isEmpty()) {
            return;
        }
        List<String> names = List.of("General Admission", "Left Premium", "Right Premium");
        List<String> types = List.of("STANDING", "SEATED", "SEATED");
        if (zones.size() != 3) {
            errors.put("zones", "Use the fixed General Admission, Left Premium, and Right Premium layout.");
            return;
        }
        for (int index = 0; index < zones.size(); index++) {
            CreateZoneRequest zone = zones.get(index);
            if (!names.get(index).equals(zone.name())) {
                errors.put("zones[" + index + "].name", "Use " + names.get(index) + ".");
            }
            if (!types.get(index).equals(zone.type())) {
                errors.put("zones[" + index + "].type", "Use " + types.get(index) + ".");
            }
        }
    }

    private void requireCapacity(Integer value, String field, Map<String, String> errors) {
        if (value == null || value < 1) {
            errors.put(field, "Capacity must be at least 1.");
        }
    }

    private void requirePrice(BigDecimal value, String field, Map<String, String> errors) {
        if (value == null || value.signum() < 0) {
            errors.put(field, "Price must be zero or more.");
        }
    }

    private void validateSaleSchedule(CreateEventRequest request, Map<String, String> errors) {
        if (request.saleType() == null) {
            errors.put("saleType", "Choose Standard or Flash.");
            return;
        }
        if (request.saleStartsAt() == null) {
            errors.put("saleStartsAt", "Sale start time is required.");
        }
        if (request.saleEndsAt() == null) {
            errors.put("saleEndsAt", "Sale end time is required.");
        }
        if (request.saleStartsAt() != null && request.startsAt() != null
                && !request.saleStartsAt().isBefore(request.startsAt())) {
            errors.put("saleStartsAt", "Sale must start before the event begins.");
        }
        if (request.saleStartsAt() != null && request.saleEndsAt() != null
                && !request.saleEndsAt().isAfter(request.saleStartsAt())) {
            errors.put("saleEndsAt", "Sale end time must be after sale start time.");
        }
        if (request.saleEndsAt() != null && request.startsAt() != null
                && request.saleEndsAt().isAfter(request.startsAt())) {
            errors.put("saleEndsAt", "Sale must end no later than the event start.");
        }
        if (request.saleType() == SaleType.FLASH) {
            if (request.queueOpensAt() == null) {
                errors.put("queueOpensAt", "Queue open time is required for flash sales.");
            } else if (request.saleStartsAt() != null && !request.queueOpensAt().isBefore(request.saleStartsAt())) {
                errors.put("queueOpensAt", "Queue must open before the flash sale starts.");
            }
        }
    }
}
