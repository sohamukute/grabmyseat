package com.grabmyseat.wallet.service;

import com.grabmyseat.wallet.model.AuditLog;
import com.grabmyseat.wallet.repository.AuditLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;

@Service
public class AuditService {

    private static final String ZERO_HASH = "0".repeat(64);

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional
    public AuditLog append(Long walletAccountId, String entityType, Long entityId,
                           String action, String payloadJson, Instant createdAt) {
        String previousHash = auditLogRepository.findFirstByWalletAccountIdOrderByIdDesc(walletAccountId)
                .map(AuditLog::getCurrentHash)
                .orElse(ZERO_HASH);
        String currentHash = computeHash(entityType, entityId, action, payloadJson, previousHash, createdAt);
        AuditLog log = new AuditLog(walletAccountId, entityType, entityId, action,
                payloadJson, previousHash, currentHash);
        return auditLogRepository.save(log);
    }

    private String computeHash(String entityType, Long entityId, String action,
                               String payloadJson, String previousHash, Instant createdAt) {
        String input = entityType + "|" + entityId + "|" + action + "|" + payloadJson
                + "|" + previousHash + "|" + createdAt;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
