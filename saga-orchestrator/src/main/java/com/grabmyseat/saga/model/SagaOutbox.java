package com.grabmyseat.saga.model;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "saga_outbox")
public class SagaOutbox {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "saga_instance_id", nullable = false)
    private SagaInstance sagaInstance;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private SagaStep step;

    @Column(name = "payload_json", nullable = false)
    private String payloadJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public SagaOutbox() {}

    public SagaOutbox(SagaInstance sagaInstance, SagaStep step, String payloadJson) {
        this.sagaInstance = sagaInstance;
        this.step = step;
        this.payloadJson = payloadJson;
    }

    public Long getId() { return id; }
    public SagaInstance getSagaInstance() { return sagaInstance; }
    public SagaStep getStep() { return step; }
    public String getPayloadJson() { return payloadJson; }
    public Instant getCreatedAt() { return createdAt; }
}
