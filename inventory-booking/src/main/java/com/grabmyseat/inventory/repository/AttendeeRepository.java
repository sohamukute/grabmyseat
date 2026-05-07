package com.grabmyseat.inventory.repository;

import com.grabmyseat.inventory.model.Attendee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AttendeeRepository extends JpaRepository<Attendee, Long> {

    List<Attendee> findByOwnerUserIdOrderByNameAsc(Long ownerUserId);

    Optional<Attendee> findByIdAndOwnerUserId(Long id, Long ownerUserId);
}
