package com.grabmyseat.inventory.model;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "events")
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String venue;

    @Column(name = "artwork_url")
    private String artworkUrl;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at")
    private Instant endsAt;

    @Column(name = "queue_opens_at", nullable = false)
    private Instant queueOpensAt;

    @Column(name = "sale_starts_at", nullable = false)
    private Instant saleStartsAt;

    @Column(name = "sale_ends_at")
    private Instant saleEndsAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "sale_type", nullable = false)
    private SaleType saleType = SaleType.STANDARD;

    @Column(name = "organizer_id", nullable = false)
    private Long organizerId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    // Set, not List: this collection and Zone.seats are both JOIN FETCHed
    // together in EventRepository.findByIdWithZonesAndSeats. A List (bag)
    // here doesn't get deduplicated by JPQL DISTINCT the way a Set does, so
    // each zone would appear once per seat row in the cartesian-product join.
    @OneToMany(mappedBy = "event", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("name")
    private Set<Zone> zones = new LinkedHashSet<>();

    protected Event() {
    }

    public Event(String name, String venue, String artworkUrl, Instant startsAt, Instant endsAt, Instant queueOpensAt,
                 Instant saleStartsAt, Instant saleEndsAt, SaleType saleType, Long organizerId) {
        this.name = name;
        this.venue = venue;
        this.artworkUrl = artworkUrl;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.queueOpensAt = queueOpensAt;
        this.saleStartsAt = saleStartsAt;
        this.saleEndsAt = saleEndsAt;
        this.saleType = saleType == null ? SaleType.STANDARD : saleType;
        this.organizerId = organizerId;
    }

    public Event(String name, String venue, String artworkUrl, Instant startsAt, Instant endsAt, Instant saleStartsAt,
                 Instant saleEndsAt, SaleType saleType, Long organizerId) {
        this(name, venue, artworkUrl, startsAt, endsAt,
                saleType == SaleType.FLASH ? saleStartsAt.minus(Duration.ofMinutes(30)) : saleStartsAt,
                saleStartsAt, saleEndsAt, saleType, organizerId);
    }

    public Event(String name, String venue, Instant startsAt, Instant endsAt, Long organizerId) {
        this(name, venue, null, startsAt, endsAt, Instant.now(), null, SaleType.STANDARD, organizerId);
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getVenue() {
        return venue;
    }

    public void setVenue(String venue) {
        this.venue = venue;
    }

    public String getArtworkUrl() {
        return artworkUrl;
    }

    public void setArtworkUrl(String artworkUrl) {
        this.artworkUrl = artworkUrl;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public void setStartsAt(Instant startsAt) {
        this.startsAt = startsAt;
    }

    public Instant getEndsAt() {
        return endsAt;
    }

    public void setEndsAt(Instant endsAt) {
        this.endsAt = endsAt;
    }

    public Instant getSaleStartsAt() { return saleStartsAt; }

    public void setSaleStartsAt(Instant saleStartsAt) { this.saleStartsAt = saleStartsAt; }

    public Instant getSaleEndsAt() { return saleEndsAt; }

    public void setSaleEndsAt(Instant saleEndsAt) { this.saleEndsAt = saleEndsAt; }

    public SaleType getSaleType() { return saleType; }

    public void setSaleType(SaleType saleType) { this.saleType = saleType == null ? SaleType.STANDARD : saleType; }

    public Instant getQueueOpensAt() { return queueOpensAt; }

    public void setQueueOpensAt(Instant queueOpensAt) { this.queueOpensAt = queueOpensAt; }

    public SaleType effectiveSaleType(Instant now) {
        return saleType == SaleType.FLASH && saleEndsAt != null && !now.isBefore(saleEndsAt)
                ? SaleType.STANDARD : saleType;
    }

    public Long getOrganizerId() {
        return organizerId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<Zone> getZones() {
        return new ArrayList<>(zones);
    }

    public void addZone(Zone zone) {
        zones.add(zone);
        zone.setEvent(this);
    }
}
