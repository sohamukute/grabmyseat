package com.grabmyseat.inventory.model;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Entity
@Table(name = "reservations")
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @Column(name = "zone_id", nullable = false)
    private Long zoneId;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "reservation_seats", joinColumns = @JoinColumn(name = "reservation_id"))
    @Column(name = "seat_id")
    private List<Long> seatIds;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "reservation_attendees", joinColumns = @JoinColumn(name = "reservation_id"))
    @OrderColumn(name = "attendee_index")
    @Column(name = "attendee_name")
    private List<String> attendeeNames;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ReservationStatus status = ReservationStatus.HELD;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "token", nullable = false, unique = true, length = 64)
    private String token;

    @Column(name = "total_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalPrice = BigDecimal.ZERO;

    protected Reservation() {
    }

    public Reservation(Long userId, Long eventId, Long zoneId, List<Long> seatIds,
                       List<String> attendeeNames, Instant expiresAt, String token, BigDecimal totalPrice) {
        this.userId = userId;
        this.eventId = eventId;
        this.zoneId = zoneId;
        this.seatIds = seatIds;
        this.attendeeNames = attendeeNames;
        this.expiresAt = expiresAt;
        this.token = token;
        this.totalPrice = totalPrice == null ? BigDecimal.ZERO : totalPrice;
    }

    public Reservation(Long userId, Long eventId, Long zoneId, List<Long> seatIds,
                       Instant expiresAt, String token, BigDecimal totalPrice) {
        this(userId, eventId, zoneId, seatIds, List.of(), expiresAt, token, totalPrice);
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getEventId() {
        return eventId;
    }

    public Long getZoneId() {
        return zoneId;
    }

    public List<Long> getSeatIds() {
        return seatIds;
    }

    public List<String> getAttendeeNames() { return attendeeNames; }

    public ReservationStatus getStatus() {
        return status;
    }

    public void setStatus(ReservationStatus status) {
        this.status = status;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getConfirmedAt() {
        return confirmedAt;
    }

    public void setConfirmedAt(Instant confirmedAt) {
        this.confirmedAt = confirmedAt;
    }

    public String getToken() {
        return token;
    }

    public BigDecimal getTotalPrice() {
        return totalPrice;
    }

    public void setTotalPrice(BigDecimal totalPrice) {
        this.totalPrice = totalPrice == null ? BigDecimal.ZERO : totalPrice;
    }
}
