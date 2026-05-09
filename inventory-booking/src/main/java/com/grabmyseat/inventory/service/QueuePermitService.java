package com.grabmyseat.inventory.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class QueuePermitService {

    private static final String PERMIT_TOKEN_PREFIX = "waiting-room:permit-token:";

    private final boolean permitRequired;
    private final StringRedisTemplate redis;

    public QueuePermitService(@Value("${waiting-room.permit-required:true}") boolean permitRequired,
                              StringRedisTemplate redis) {
        this.permitRequired = permitRequired;
        this.redis = redis;
    }

    public void validate(String permitToken, Long eventId, Long userId) {
        if (!permitRequired) {
            return;
        }
        if (permitToken == null || permitToken.isBlank()) {
            throw new com.grabmyseat.inventory.security.MissingQueuePermitException("queue permit is required");
        }

        // Written by waiting-room via plain Spring Data RedisTemplate (see
        // AdmissionService.admitNext) - read it with the same client here.
        // Redisson's RMap uses its own codec and cannot decode values written
        // by a different Redis client, which silently made every permit look
        // "invalid" despite the underlying hash being correct.
        String key = PERMIT_TOKEN_PREFIX + permitToken;
        Map<Object, Object> meta = redis.opsForHash().entries(key);
        Object storedEventId = meta.get("eventId");
        Object storedUserId = meta.get("userId");

        if (storedEventId == null || storedUserId == null) {
            throw new com.grabmyseat.inventory.security.InvalidQueuePermitException("invalid or expired queue permit");
        }

        if (!storedEventId.toString().equals(String.valueOf(eventId))) {
            throw new com.grabmyseat.inventory.security.InvalidQueuePermitException("queue permit is for a different event");
        }

        if (!storedUserId.toString().equals(String.valueOf(userId))) {
            throw new com.grabmyseat.inventory.security.InvalidQueuePermitException("queue permit does not belong to this user");
        }

        // Consume the permit so it cannot be reused.
        redis.delete(key);
    }
}
