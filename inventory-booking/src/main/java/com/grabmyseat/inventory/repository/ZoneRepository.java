package com.grabmyseat.inventory.repository;

import com.grabmyseat.inventory.model.Zone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ZoneRepository extends JpaRepository<Zone, Long> {

    List<Zone> findByEventIdOrderByName(Long eventId);

    @Query("SELECT z FROM Zone z LEFT JOIN FETCH z.seats WHERE z.id = :id")
    Optional<Zone> findByIdWithSeats(@Param("id") Long id);
}
