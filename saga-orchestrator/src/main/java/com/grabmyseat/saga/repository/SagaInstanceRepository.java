package com.grabmyseat.saga.repository;

import com.grabmyseat.saga.model.SagaInstance;
import com.grabmyseat.saga.model.SagaStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SagaInstanceRepository extends JpaRepository<SagaInstance, Long> {
    Optional<SagaInstance> findByReservationToken(String reservationToken);
    List<SagaInstance> findByStatusAndUpdatedAtBefore(SagaStatus status, Instant updatedAt);
}
