package com.grabmyseat.inventory.repository;

import com.grabmyseat.inventory.model.Venue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface VenueRepository extends JpaRepository<Venue, Long> {

    List<Venue> findAllByOrderByCityAsc();

    @Query(value = "SELECT id FROM venue WHERE id = :id FOR UPDATE", nativeQuery = true)
    Long lock(Long id);
}
