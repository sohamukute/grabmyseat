package com.grabmyseat.ticketing.model;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

@Entity
@Table(name = "tickets")
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reservation_token", nullable = false, unique = true, length = 128)
    private String reservationToken;

    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @Column(name = "zone_id", nullable = false)
    private Long zoneId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "ticket_seats", joinColumns = @JoinColumn(name = "ticket_id"))
    @Column(name = "seat_id")
    private List<Long> seatIds;

    @Column(name = "holder_name", nullable = false, length = 120)
    private String holderName;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "ticket_attendees", joinColumns = @JoinColumn(name = "ticket_id"))
    @OrderColumn(name = "attendee_index")
    @Column(name = "attendee_name")
    private List<String> attendeeNames;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "ticket_attendance", joinColumns = @JoinColumn(name = "ticket_id"))
    @MapKeyColumn(name = "attendee_name")
    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private Map<String, AttendanceStatus> attendance = new LinkedHashMap<>();

    @Column(name = "qr_payload", nullable = false, length = 2048)
    private String qrPayload;

    @Column(name = "qr_rotated_at")
    private Instant qrRotatedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "used_at")
    private Instant usedAt;

    protected Ticket() {
    }

    public Ticket(String reservationToken, Long eventId, Long zoneId, Long userId,
                  List<Long> seatIds, List<String> attendeeNames, String qrPayload) {
        this.reservationToken = reservationToken;
        this.eventId = eventId;
        this.zoneId = zoneId;
        this.userId = userId;
        this.seatIds = seatIds;
        this.attendeeNames = attendeeNames;
        this.holderName = attendeeNames.getFirst();
        attendeeNames.forEach(name -> attendance.put(name, AttendanceStatus.PENDING));
        this.qrPayload = qrPayload;
    }

    public Ticket(String reservationToken, Long eventId, Long zoneId, Long userId,
                  List<Long> seatIds, String qrPayload) {
        this(reservationToken, eventId, zoneId, userId, seatIds, List.of("Guest"), qrPayload);
    }

    public Long getId() {
        return id;
    }

    public String getReservationToken() {
        return reservationToken;
    }

    public Long getEventId() {
        return eventId;
    }

    public Long getZoneId() {
        return zoneId;
    }

    public Long getUserId() {
        return userId;
    }

    public List<Long> getSeatIds() {
        return seatIds;
    }

    public String getHolderName() { return holderName; }

    public List<String> getAttendeeNames() { return attendeeNames; }

    public Map<String, AttendanceStatus> getAttendance() { return attendance; }

    public String getQrPayload() {
        return qrPayload;
    }

    public void setQrPayload(String qrPayload) {
        this.qrPayload = qrPayload;
    }

    public Instant getQrRotatedAt() {
        return qrRotatedAt;
    }

    public void setQrRotatedAt(Instant qrRotatedAt) {
        this.qrRotatedAt = qrRotatedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUsedAt() {
        return usedAt;
    }

    public void setUsedAt(Instant usedAt) {
        this.usedAt = usedAt;
    }
}
