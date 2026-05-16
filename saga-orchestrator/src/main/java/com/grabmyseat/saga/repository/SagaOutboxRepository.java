package com.grabmyseat.saga.repository;

import com.grabmyseat.saga.model.SagaOutbox;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SagaOutboxRepository extends JpaRepository<SagaOutbox, Long> {
    List<SagaOutbox> findBySagaInstanceIdOrderByIdAsc(Long sagaInstanceId);
}
