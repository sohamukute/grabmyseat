package com.grabmyseat.waitingroom.service;

import com.grabmyseat.waitingroom.RedisKeys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class AdmissionService {

    private final RedisTemplate<String, String> redis;

    @Value("${waiting-room.admission-rate-per-second:10}")
    private int admissionRatePerSecond;

    @Value("${waiting-room.permit-ttl-minutes:10}")
    private long permitTtlMinutes;

    public AdmissionService(RedisTemplate<String, String> redis) {
        this.redis = redis;
    }

    public Optional<String> admitNext(Long eventId, QueueService queueService) {
        String rateKey = RedisKeys.RATE_PREFIX + eventId;
        Long current = redis.opsForValue().increment(rateKey);
        if (current == null) {
            return Optional.empty();
        }
        if (current == 1L) {
            redis.expire(rateKey, Duration.ofSeconds(1));
        }
        if (current > admissionRatePerSecond) {
            return Optional.empty();
        }

        String queueKey = queueService.queueKey(eventId);
        Set<String> tokens = redis.opsForZSet().range(queueKey, 0, 0);
        if (tokens == null || tokens.isEmpty()) {
            return Optional.empty();
        }

        String token = tokens.iterator().next();
        redis.opsForZSet().remove(queueKey, token);

        String permitToken = UUID.randomUUID().toString();
        String permitKey = queueService.permitKey(token);
        redis.opsForValue().set(permitKey, permitToken, Duration.ofMinutes(permitTtlMinutes));

        Long userId = queueService.extractUserId(token);
        Long tokenEventId = queueService.extractEventId(token);
        if (userId != null && tokenEventId != null) {
            String permitTokenKey = queueService.permitTokenKey(permitToken);
            redis.opsForHash().putAll(permitTokenKey, Map.of(
                    "eventId", String.valueOf(tokenEventId),
                    "userId", String.valueOf(userId),
                    "queueToken", token
            ));
            redis.expire(permitTokenKey, Duration.ofMinutes(permitTtlMinutes));
        }

        return Optional.of(token);
    }

    public Long estimateWait(long position, Long eventId) {
        int rate = admissionRatePerSecond;
        if (rate <= 0) {
            return Long.MAX_VALUE;
        }
        return position / rate;
    }

    public int admissionRate() {
        return admissionRatePerSecond;
    }
}
