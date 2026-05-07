package com.grabmyseat.inventory.model;

import jakarta.persistence.*;

@Entity
@Table(name = "seats", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"zone_id", "row_label", "number"}, name = "uk_seat_zone_row_number")
})
public class Seat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "row_label", nullable = false, length = 8)
    private String rowLabel;

    @Column(nullable = false)
    private Integer number;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private SeatStatus status = SeatStatus.AVAILABLE;

    @Version
    @Column(nullable = false)
    private Long version = 0L;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "zone_id", nullable = false)
    private Zone zone;

    protected Seat() {
    }

    public Seat(String rowLabel, Integer number) {
        this.rowLabel = rowLabel;
        this.number = number;
    }

    public Long getId() {
        return id;
    }

    public String getRowLabel() {
        return rowLabel;
    }

    public void setRowLabel(String rowLabel) {
        this.rowLabel = rowLabel;
    }

    public Integer getNumber() {
        return number;
    }

    public void setNumber(Integer number) {
        this.number = number;
    }

    public SeatStatus getStatus() {
        return status;
    }

    public void setStatus(SeatStatus status) {
        this.status = status;
    }

    public Long getVersion() {
        return version;
    }

    public Zone getZone() {
        return zone;
    }

    public void setZone(Zone zone) {
        this.zone = zone;
    }
}
