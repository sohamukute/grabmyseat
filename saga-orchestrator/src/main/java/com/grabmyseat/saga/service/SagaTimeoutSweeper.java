package com.grabmyseat.saga.service;

import com.grabmyseat.saga.model.SagaInstance;
import com.grabmyseat.saga.model.SagaStatus;
import com.grabmyseat.saga.repository.SagaInstanceRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
public class SagaTimeoutSweeper {

    private final SagaInstanceRepository sagaInstanceRepository;
    private final SagaService sagaService;

    @Value("${saga.timeout-seconds:30}")
    private long timeoutSeconds;

    public SagaTimeoutSweeper(SagaInstanceRepository sagaInstanceRepository, SagaService sagaService) {
        this.sagaInstanceRepository = sagaInstanceRepository;
        this.sagaService = sagaService;
    }

    @Transactional
    @Scheduled(fixedDelayString = "${saga.sweep-interval-ms:60000}")
    public void timeoutStuckSagas() {
        Instant cutoff = Instant.now().minus(timeoutSeconds, ChronoUnit.SECONDS);
        List<SagaInstance> stuck = sagaInstanceRepository.findByStatusAndUpdatedAtBefore(SagaStatus.DEBITED, cutoff);
        for (SagaInstance saga : stuck) {
            try {
                sagaService.compensate(saga, saga.getUserId());
            } catch (Exception ex) {
                // compensation will retry on next sweep
            }
        }
    }
}
