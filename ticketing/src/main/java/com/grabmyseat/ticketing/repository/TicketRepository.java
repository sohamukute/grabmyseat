package com.grabmyseat.ticketing.repository;

import com.grabmyseat.ticketing.model.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, Long> {

    Optional<Ticket> findByReservationToken(String reservationToken);

    List<Ticket> findAllByUserIdOrderByCreatedAtDesc(Long userId);
}
