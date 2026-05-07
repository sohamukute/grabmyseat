package com.grabmyseat.inventory.repository;

import com.grabmyseat.inventory.model.Event;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EventRepository extends JpaRepository<Event, Long> {

    List<Event> findAllByOrderByStartsAtDesc();

    List<Event> findByOrganizerIdOrderByStartsAtDesc(Long organizerId);

    @Query("SELECT e FROM Event e LEFT JOIN FETCH e.zones WHERE e.id = :id")
    Optional<Event> findByIdWithZones(@Param("id") Long id);

    @Query("SELECT DISTINCT e FROM Event e LEFT JOIN FETCH e.zones z LEFT JOIN FETCH z.seats WHERE e.id = :id")
    Optional<Event> findByIdWithZonesAndSeats(@Param("id") Long id);
}
