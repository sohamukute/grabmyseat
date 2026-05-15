package com.grabmyseat.inventory.service;

import com.grabmyseat.inventory.dto.CreateEventRequest;
import com.grabmyseat.inventory.dto.CreateZoneRequest;
import com.grabmyseat.inventory.dto.EventLayoutRequest;
import com.grabmyseat.inventory.dto.EventResponse;
import com.grabmyseat.inventory.model.Event;
import com.grabmyseat.inventory.model.SaleType;
import com.grabmyseat.inventory.repository.EventRepository;
import com.grabmyseat.inventory.repository.SeatRepository;
import com.grabmyseat.inventory.repository.ZoneRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    @Mock private EventRepository eventRepository;
    @Mock private ZoneRepository zoneRepository;
    @Mock private SeatRepository seatRepository;
    @Mock private CapacityService capacityService;
    @InjectMocks private EventService eventService;

    @Test
    void rejectsLegacyGeneralAdmissionZoneType() {
        CreateZoneRequest legacyZone = new CreateZoneRequest(
                "General Admission", 100, new BigDecimal("499.00"), List.of(), "GENERAL_ADMISSION");

        assertThatThrownBy(() -> eventService.createEvent(42L, legacyRequest(List.of(legacyZone))))
                .isInstanceOf(EventValidationException.class)
                .satisfies(error -> assertThat(((EventValidationException) error).fieldErrors())
                        .containsEntry("zones[0].type", "Use STANDING or SEATED."));
    }

    @Test
    void rejectsMissingFixedZone() {
        EventLayoutRequest layout = new EventLayoutRequest(
                100, new BigDecimal("499.00"),
                20, new BigDecimal("999.00"),
                null, new BigDecimal("999.00"));

        assertThatThrownBy(() -> eventService.createEvent(42L, request(layout, SaleType.STANDARD, null)))
                .isInstanceOf(EventValidationException.class)
                .satisfies(error -> assertThat(((EventValidationException) error).fieldErrors())
                        .containsKey("layout.rightPremiumCapacity"));
    }

    @Test
    void createsExactlyTheFixedStandingAndPremiumSeatedZones() {
        when(eventRepository.save(any(Event.class))).thenAnswer(invocation -> {
            Event event = invocation.getArgument(0);
            long id = 1;
            for (var zone : event.getZones()) {
                ReflectionTestUtils.setField(zone, "id", id++);
            }
            return event;
        });
        EventLayoutRequest layout = new EventLayoutRequest(
                100, new BigDecimal("499.00"),
                2, new BigDecimal("999.00"),
                3, new BigDecimal("1099.00"));

        EventResponse response = eventService.createEvent(42L, request(layout, SaleType.STANDARD, null));

        assertThat(response.zones()).extracting(EventResponse.ZoneResponse::name)
                .containsExactly("General Admission", "Left Premium", "Right Premium");
        assertThat(response.zones()).extracting(EventResponse.ZoneResponse::type)
                .containsExactly("STANDING", "SEATED", "SEATED");
        assertThat(response.zones().get(0).seats()).hasSize(100);
        assertThat(response.zones().get(1).seats()).hasSize(2);
        assertThat(response.zones().get(2).seats()).hasSize(3);
    }

    @Test
    void flashSaleRequiresQueueOpenAndValidSaleWindow() {
        EventLayoutRequest layout = new EventLayoutRequest(
                100, new BigDecimal("499.00"),
                2, new BigDecimal("999.00"),
                3, new BigDecimal("1099.00"));

        assertThatThrownBy(() -> eventService.createEvent(42L, request(layout, SaleType.FLASH, null)))
                .isInstanceOf(EventValidationException.class)
                .satisfies(error -> assertThat(((EventValidationException) error).fieldErrors())
                        .containsEntry("queueOpensAt", "Queue open time is required for flash sales."));
    }

    private CreateEventRequest request(EventLayoutRequest layout, SaleType saleType, Instant queueOpensAt) {
        Instant eventStarts = Instant.parse("2030-02-01T18:00:00Z");
        return new CreateEventRequest(
                "Concert", "Arena", "https://example.test/poster.jpg",
                eventStarts, eventStarts.plusSeconds(7200), queueOpensAt,
                Instant.parse("2030-01-01T10:00:00Z"), Instant.parse("2030-01-31T10:00:00Z"),
                saleType, layout, null);
    }

    private CreateEventRequest legacyRequest(List<CreateZoneRequest> zones) {
        Instant eventStarts = Instant.parse("2030-02-01T18:00:00Z");
        return new CreateEventRequest(
                "Concert", "Arena", "https://example.test/poster.jpg",
                eventStarts, eventStarts.plusSeconds(7200),
                Instant.parse("2030-01-01T10:00:00Z"), Instant.parse("2030-01-31T10:00:00Z"),
                SaleType.STANDARD, zones);
    }
}
