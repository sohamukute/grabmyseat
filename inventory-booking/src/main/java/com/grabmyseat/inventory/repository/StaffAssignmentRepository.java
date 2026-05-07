package com.grabmyseat.inventory.repository;

import com.grabmyseat.inventory.model.StaffAssignment;
import com.grabmyseat.inventory.model.StaffAssignmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StaffAssignmentRepository extends JpaRepository<StaffAssignment, Long> {

    Optional<StaffAssignment> findByEventIdAndUserId(Long eventId, Long userId);

    List<StaffAssignment> findByEventId(Long eventId);

    boolean existsByEventIdAndUserIdAndStatus(Long eventId, Long userId, StaffAssignmentStatus status);
}
