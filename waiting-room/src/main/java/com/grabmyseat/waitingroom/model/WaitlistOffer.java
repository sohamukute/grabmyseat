package com.grabmyseat.waitingroom.model;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "waitlist_offers")
public class WaitlistOffer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 255)
    private String token;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @Column(name = "zone_id", nullable = false)
    private Long zoneId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OfferStatus status;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public WaitlistOffer() {}

    public WaitlistOffer(String token, Long userId, Long eventId, Long zoneId,
                         OfferStatus status, Instant expiresAt) {
        this.token = token;
        this.userId = userId;
        this.eventId = eventId;
        this.zoneId = zoneId;
        this.status = status;
        this.expiresAt = expiresAt;
    }

    public Long getId() {
        return id;
    }

    public String getToken() {
        return token;
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

    public OfferStatus getStatus() {
        return status;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setStatus(OfferStatus status) {
        this.status = status;
        this.updatedAt = Instant.now();
    }
}
