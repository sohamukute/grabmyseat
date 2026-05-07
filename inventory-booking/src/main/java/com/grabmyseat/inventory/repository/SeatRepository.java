package com.grabmyseat.inventory.repository;

import com.grabmyseat.inventory.model.Seat;
import com.grabmyseat.inventory.model.SeatStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SeatRepository extends JpaRepository<Seat, Long> {

    List<Seat> findByZoneIdOrderByRowLabelAscNumberAsc(Long zoneId);

    Optional<Seat> findByIdAndZoneId(Long id, Long zoneId);

    @Modifying
    @Query("UPDATE Seat s SET s.status = :status WHERE s.id = :id AND s.status = :currentStatus")
    int updateStatus(@Param("id") Long id,
                     @Param("currentStatus") SeatStatus currentStatus,
                     @Param("status") SeatStatus status);

    long countByZoneIdAndStatus(Long zoneId, SeatStatus status);
}
