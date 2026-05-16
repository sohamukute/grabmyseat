package com.grabmyseat.saga.model;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Entity
@Table(name = "saga_instances")
public class SagaInstance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reservation_token", nullable = false, unique = true, length = 255)
    private String reservationToken;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @Column(name = "zone_id", nullable = false)
    private Long zoneId;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "saga_instance_seat_ids", joinColumns = @JoinColumn(name = "saga_instance_id"))
    @Column(name = "seat_id")
    private List<Long> seatIds;

    @Column(name = "total_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SagaStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    public SagaInstance() {}

    public SagaInstance(String reservationToken, Long userId, Long eventId, Long zoneId,
                        List<Long> seatIds, BigDecimal totalAmount, SagaStatus status, Instant expiresAt) {
        this.reservationToken = reservationToken;
        this.userId = userId;
        this.eventId = eventId;
        this.zoneId = zoneId;
        this.seatIds = seatIds;
        this.totalAmount = totalAmount;
        this.status = status;
        this.expiresAt = expiresAt;
    }

    public Long getId() { return id; }
    public String getReservationToken() { return reservationToken; }
    public Long getUserId() { return userId; }
    public Long getEventId() { return eventId; }
    public Long getZoneId() { return zoneId; }
    public List<Long> getSeatIds() { return seatIds; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public SagaStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getExpiresAt() { return expiresAt; }

    public void setStatus(SagaStatus status) {
        this.status = status;
        this.updatedAt = Instant.now();
    }
}
