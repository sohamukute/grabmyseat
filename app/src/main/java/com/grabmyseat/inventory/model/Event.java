package com.grabmyseat.inventory.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "event")
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "venue_id")
    private Venue venue;

    @Column(name = "organizer_id", nullable = false)
    private Long organizerId;

    @Column(nullable = false)
    private String title;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Column(name = "booking_opens_at", nullable = false)
    private Instant bookingOpensAt;

    @Column(name = "booking_closes_at", nullable = false)
    private Instant bookingClosesAt;

    @Column(name = "max_per_person", nullable = false)
    private int maxPerPerson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EventStatus status = EventStatus.DRAFT;

    @Column(nullable = false)
    private String category;

    @Column(nullable = false)
    private String tagline;

    @Column(nullable = false)
    private String description;

    @Column(name = "presented_by", nullable = false)
    private String presentedBy;

    @Column(nullable = false)
    private String language;

    @Column(name = "age_guidance", nullable = false)
    private String ageGuidance;

    @Column(name = "waiting_room", nullable = false)
    private boolean waitingRoom;

    protected Event() {
    }

    public Event(Venue venue, Long organizerId, String title, Instant startsAt, Instant endsAt,
                 Instant bookingOpensAt, Instant bookingClosesAt, int maxPerPerson, String category,
                 String tagline, String description, String presentedBy, String language, String ageGuidance) {
        this.venue = venue;
        this.organizerId = organizerId;
        this.title = title;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.bookingOpensAt = bookingOpensAt;
        this.bookingClosesAt = bookingClosesAt;
        this.maxPerPerson = maxPerPerson;
        this.category = category;
        this.tagline = tagline;
        this.description = description;
        this.presentedBy = presentedBy;
        this.language = language;
        this.ageGuidance = ageGuidance;
    }

    public Long getId() {
        return id;
    }

    public Venue getVenue() {
        return venue;
    }

    public Long getOrganizerId() {
        return organizerId;
    }

    public String getTitle() {
        return title;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public Instant getEndsAt() {
        return endsAt;
    }

    public Instant getBookingOpensAt() {
        return bookingOpensAt;
    }

    public Instant getBookingClosesAt() {
        return bookingClosesAt;
    }

    public int getMaxPerPerson() {
        return maxPerPerson;
    }

    public EventStatus getStatus() {
        return status;
    }

    public String getCategory() {
        return category;
    }

    public String getTagline() {
        return tagline;
    }

    public String getDescription() {
        return description;
    }

    public String getPresentedBy() {
        return presentedBy;
    }

    public String getLanguage() {
        return language;
    }

    public String getAgeGuidance() {
        return ageGuidance;
    }

    public boolean isWaitingRoom() {
        return waitingRoom;
    }
}
