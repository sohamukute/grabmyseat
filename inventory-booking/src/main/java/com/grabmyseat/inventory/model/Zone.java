package com.grabmyseat.inventory.model;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "zones")
public class Zone {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private Integer capacity;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal price;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    // Set, not List: Event.zones is also fetched eagerly alongside this
    // collection in EventRepository.findByIdWithZonesAndSeats, and Hibernate
    // can't JOIN FETCH two List ("bag") collections in the same query
    // (MultipleBagFetchException). One side has to be a Set.
    @OneToMany(mappedBy = "zone", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("rowLabel, number")
    private Set<Seat> seats = new LinkedHashSet<>();

    protected Zone() {
    }

    public Zone(String name, Integer capacity, BigDecimal price) {
        this.name = name;
        this.capacity = capacity;
        this.price = price;
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

    public Integer getCapacity() {
        return capacity;
    }

    public void setCapacity(Integer capacity) {
        this.capacity = capacity;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public Event getEvent() {
        return event;
    }

    public void setEvent(Event event) {
        this.event = event;
    }

    public List<Seat> getSeats() {
        return new ArrayList<>(seats);
    }

    public void addSeat(Seat seat) {
        seats.add(seat);
        seat.setZone(this);
    }
}
