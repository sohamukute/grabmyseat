package com.grabmyseat.wallet.model;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "audit_log")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "wallet_account_id", nullable = false)
    private Long walletAccountId;

    @Column(name = "entity_type", nullable = false, length = 50)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    @Column(nullable = false, length = 50)
    private String action;

    @Column(name = "payload_json", nullable = false)
    private String payloadJson;

    @Column(name = "previous_hash", nullable = false, length = 64)
    private String previousHash;

    @Column(name = "current_hash", nullable = false, length = 64)
    private String currentHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public AuditLog() {}

    public AuditLog(Long walletAccountId, String entityType, Long entityId, String action,
                    String payloadJson, String previousHash, String currentHash) {
        this.walletAccountId = walletAccountId;
        this.entityType = entityType;
        this.entityId = entityId;
        this.action = action;
        this.payloadJson = payloadJson;
        this.previousHash = previousHash;
        this.currentHash = currentHash;
    }

    public Long getId() {
        return id;
    }

    public Long getWalletAccountId() {
        return walletAccountId;
    }

    public String getEntityType() {
        return entityType;
    }

    public Long getEntityId() {
        return entityId;
    }

    public String getAction() {
        return action;
    }

    public String getPayloadJson() {
        return payloadJson;
    }

    public String getPreviousHash() {
        return previousHash;
    }

    public String getCurrentHash() {
        return currentHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
