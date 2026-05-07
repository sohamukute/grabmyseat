package com.grabmyseat.inventory.repository;

import com.grabmyseat.inventory.model.Reservation;
import com.grabmyseat.inventory.model.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    Optional<Reservation> findByToken(String token);

    List<Reservation> findByStatus(ReservationStatus status);

    List<Reservation> findByStatusAndExpiresAtBefore(ReservationStatus status, Instant now);

    @Modifying
    @Query("UPDATE Reservation r SET r.status = :status WHERE r.token = :token AND r.status = :currentStatus")
    int updateStatus(@Param("token") String token,
                     @Param("currentStatus") ReservationStatus currentStatus,
                     @Param("status") ReservationStatus status);

    @Query("""
            SELECT COALESCE(SUM(SIZE(r.seatIds)), 0)
            FROM Reservation r
            WHERE r.userId = :userId
              AND r.eventId = :eventId
              AND r.status IN :statuses
            """)
    long sumQuantityByUserIdAndEventIdAndStatusIn(@Param("userId") Long userId,
                                                   @Param("eventId") Long eventId,
                                                   @Param("statuses") List<ReservationStatus> statuses);
}
