package com.grabmyseat.waitingroom.service;

import com.grabmyseat.waitingroom.RedisKeys;
import com.grabmyseat.waitingroom.client.InventorySaleClient;
import com.grabmyseat.waitingroom.dto.JoinQueueResponse;
import com.grabmyseat.waitingroom.dto.QueuePositionResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class QueueService {

    private final RedisTemplate<String, String> redis;
    private final AdmissionService admissionService;
    private final InventorySaleClient inventorySaleClient;

    @Value("${waiting-room.queue-token-ttl-hours:2}")
    private long queueTokenTtlHours;

    public QueueService(RedisTemplate<String, String> redis, AdmissionService admissionService,
                        InventorySaleClient inventorySaleClient) {
        this.redis = redis;
        this.admissionService = admissionService;
        this.inventorySaleClient = inventorySaleClient;
    }

    public JoinQueueResponse join(Long userId, Long eventId) {
        inventorySaleClient.requireQueueAccess(eventId);
        String activeKey = activeTokenKey(userId, eventId);
        String existingToken = redis.opsForValue().get(activeKey);
        if (existingToken != null) {
            removeFromQueue(eventId, existingToken);
            redis.delete(tokenMetaKey(existingToken));
        }

        String token = UUID.randomUUID().toString();
        long score = System.currentTimeMillis();

        redis.opsForZSet().add(queueKey(eventId), token, score);
        redis.opsForValue().set(activeKey, token, queueTokenTtlHours, TimeUnit.HOURS);
        redis.opsForHash().putAll(tokenMetaKey(token), Map.of(
                "userId", String.valueOf(userId),
                "eventId", String.valueOf(eventId),
                "joinedAt", String.valueOf(score)
        ));
        redis.expire(tokenMetaKey(token), Duration.ofHours(queueTokenTtlHours));
        redis.opsForSet().add(RedisKeys.ACTIVE_EVENTS_KEY, String.valueOf(eventId));

        Long position = redis.opsForZSet().rank(queueKey(eventId), token);
        long pos = position == null ? 0L : position;
        Long estimatedWait = admissionService.estimateWait(pos, eventId);
        return new JoinQueueResponse(token, pos, estimatedWait);
    }

    public QueuePositionResponse position(Long eventId, String token) {
        String permit = redis.opsForValue().get(permitKey(token));
        if (permit != null) {
            return new QueuePositionResponse(token, 0L, "ADMITTED");
        }

        Long rank = redis.opsForZSet().rank(queueKey(eventId), token);
        if (rank == null) {
            return new QueuePositionResponse(token, -1L, "UNKNOWN");
        }
        return new QueuePositionResponse(token, rank, "WAITING");
    }

    /**
     * Queue tokens are bearer-like secrets, but they are never sufficient by
     * themselves. Every browser action must also be made by the account that
     * joined the queue. This closes token sharing / token-leak queue jumps.
     */
    public QueuePositionResponse position(Long userId, Long eventId, String token) {
        assertOwnedBy(userId, eventId, token);
        return position(eventId, token);
    }

    public void leave(Long eventId, String token) {
        removeFromQueue(eventId, token);
        redis.delete(permitKey(token));

        String metaKey = tokenMetaKey(token);
        Object userIdObj = redis.opsForHash().get(metaKey, "userId");
        if (userIdObj != null) {
            try {
                Long userId = Long.valueOf(userIdObj.toString());
                redis.delete(activeTokenKey(userId, eventId));
            } catch (NumberFormatException ignored) {
                // best-effort cleanup
            }
        }
        redis.delete(metaKey);
    }

    public void leave(Long userId, Long eventId, String token) {
        assertOwnedBy(userId, eventId, token);
        leave(eventId, token);
    }

    private void removeFromQueue(Long eventId, String token) {
        redis.opsForZSet().remove(queueKey(eventId), token);
    }

    public String queueKey(Long eventId) {
        return RedisKeys.QUEUE_PREFIX + eventId;
    }

    public String activeTokenKey(Long userId, Long eventId) {
        return RedisKeys.ACTIVE_TOKEN_PREFIX + userId + ":" + eventId;
    }

    public String tokenMetaKey(String token) {
        return RedisKeys.TOKEN_META_PREFIX + token;
    }

    public String permitKey(String token) {
        return RedisKeys.PERMIT_PREFIX + token;
    }

    public String permitTokenKey(String permitToken) {
        return RedisKeys.PERMIT_TOKEN_PREFIX + permitToken;
    }

    public Long extractUserId(String token) {
        Object userId = redis.opsForHash().get(tokenMetaKey(token), "userId");
        return userId == null ? null : Long.valueOf(userId.toString());
    }

    public Long extractEventId(String token) {
        Object eventId = redis.opsForHash().get(tokenMetaKey(token), "eventId");
        return eventId == null ? null : Long.valueOf(eventId.toString());
    }

    public void removeActiveEvent(Long eventId) {
        redis.opsForSet().remove(RedisKeys.ACTIVE_EVENTS_KEY, String.valueOf(eventId));
    }

    public Set<String> activeEventIds() {
        Set<String> members = redis.opsForSet().members(RedisKeys.ACTIVE_EVENTS_KEY);
        return members == null ? Set.of() : members;
    }

    public Long queueSize(Long eventId) {
        return redis.opsForZSet().size(queueKey(eventId));
    }

    public String permitValue(String token) {
        return redis.opsForValue().get(permitKey(token));
    }

    public String permitValue(Long userId, Long eventId, String token) {
        assertOwnedBy(userId, eventId, token);
        return permitValue(token);
    }

    public void assertOwnedBy(Long userId, Long eventId, String token) {
        Object storedUserId = redis.opsForHash().get(tokenMetaKey(token), "userId");
        Object storedEventId = redis.opsForHash().get(tokenMetaKey(token), "eventId");
        if (storedUserId == null || storedEventId == null
                || !String.valueOf(userId).equals(storedUserId.toString())
                || !String.valueOf(eventId).equals(storedEventId.toString())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "queue token does not belong to this account");
        }
    }

    public Set<Long> waitingUserIds(Long eventId) {
        Set<String> tokens = redis.opsForZSet().range(queueKey(eventId), 0, -1);
        if (tokens == null) {
            return Set.of();
        }
        return tokens.stream()
                .map(this::extractUserId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());
    }
}
