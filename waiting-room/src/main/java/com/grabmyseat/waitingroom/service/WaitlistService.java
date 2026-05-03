package com.grabmyseat.waitingroom.service;

import com.grabmyseat.waitingroom.dto.ReleaseNotification;
import com.grabmyseat.waitingroom.client.InventorySaleClient;
import com.grabmyseat.waitingroom.dto.WaitlistJoinResponse;
import com.grabmyseat.waitingroom.dto.WaitlistStatusResponse;
import com.grabmyseat.waitingroom.model.OfferStatus;
import com.grabmyseat.waitingroom.model.WaitlistOffer;
import com.grabmyseat.waitingroom.repository.WaitlistOfferRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class WaitlistService {

    static final String WAITLIST_PREFIX = "waitlist:";
    static final String ACTIVE_TOKEN_PREFIX = "waitlist:active-token:";
    static final String TOKEN_META_PREFIX = "waitlist:token:";
    static final String OFFER_PREFIX = "waitlist:offer:";
    static final String PERMIT_PREFIX = "waitlist:permit:";

    private final RedisTemplate<String, String> redis;
    private final WaitlistOfferRepository offerRepository;
    private final Counter waitlistOffersSentCounter;
    private final InventorySaleClient inventorySaleClient;

    @Value("${waiting-room.offer-ttl-minutes:5}")
    private long offerTtlMinutes;

    @Value("${waiting-room.permit-ttl-minutes:10}")
    private long permitTtlMinutes;

    public WaitlistService(RedisTemplate<String, String> redis,
                           WaitlistOfferRepository offerRepository,
                           InventorySaleClient inventorySaleClient,
                           MeterRegistry meterRegistry) {
        this.redis = redis;
        this.offerRepository = offerRepository;
        this.inventorySaleClient = inventorySaleClient;
        this.waitlistOffersSentCounter = meterRegistry == null ? null :
                Counter.builder("grabmyseat.waitlist.offers.sent")
                        .description("Total number of waitlist offers sent")
                        .register(meterRegistry);
    }

    @Transactional
    public WaitlistJoinResponse join(Long userId, Long eventId, Long zoneId) {
        inventorySaleClient.requireInterestAccess(eventId);
        String activeKey = activeTokenKey(userId, eventId, zoneId);
        String existingToken = redis.opsForValue().get(activeKey);
        if (existingToken != null) {
            removeFromWaitlist(eventId, zoneId, existingToken);
            redis.delete(tokenMetaKey(existingToken));
            offerRepository.findByToken(existingToken).ifPresent(existingOffer -> {
                if (existingOffer.getStatus() == OfferStatus.PENDING) {
                    existingOffer.setStatus(OfferStatus.EXPIRED);
                    offerRepository.save(existingOffer);
                }
            });
        }

        String token = UUID.randomUUID().toString();
        long score = System.currentTimeMillis();

        redis.opsForZSet().add(waitlistKey(eventId, zoneId), token, score);
        redis.opsForValue().set(activeKey, token, 24, TimeUnit.HOURS);
        redis.opsForHash().putAll(tokenMetaKey(token), Map.of(
                "userId", String.valueOf(userId),
                "eventId", String.valueOf(eventId),
                "zoneId", String.valueOf(zoneId),
                "joinedAt", String.valueOf(score)
        ));
        redis.expire(tokenMetaKey(token), Duration.ofHours(24));

        Long position = redis.opsForZSet().rank(waitlistKey(eventId, zoneId), token);
        long pos = position == null ? 0L : position;
        return new WaitlistJoinResponse(token, pos);
    }

    @Transactional(readOnly = true)
    public WaitlistStatusResponse status(String token) {
        Optional<WaitlistOffer> offer = offerRepository.findByToken(token);
        if (offer.isPresent()) {
            WaitlistOffer o = offer.get();
            String permitToken = o.getStatus() == OfferStatus.ACCEPTED
                    ? redis.opsForValue().get(permitKey(token))
                    : null;
            return new WaitlistStatusResponse(token, o.getStatus(), o.getExpiresAt(), permitToken);
        }

        Long rank = rankInWaitlist(token);
        if (rank != null) {
            return new WaitlistStatusResponse(token, null, null, null);
        }
        return new WaitlistStatusResponse(token, null, null, null);
    }

    @Transactional
    public WaitlistStatusResponse accept(String token) {
        WaitlistOffer offer = offerRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("offer not found"));
        if (offer.getStatus() != OfferStatus.PENDING) {
            throw new IllegalStateException("offer is not pending");
        }
        if (offer.getExpiresAt().isBefore(Instant.now())) {
            offer.setStatus(OfferStatus.EXPIRED);
            offerRepository.save(offer);
            throw new IllegalStateException("offer expired");
        }
        offer.setStatus(OfferStatus.ACCEPTED);
        offerRepository.save(offer);
        String permitToken = UUID.randomUUID().toString();
        redis.opsForValue().set(permitKey(token), permitToken, permitTtlMinutes, TimeUnit.MINUTES);
        redis.opsForHash().put(offerKey(token), "status", OfferStatus.ACCEPTED.name());
        return new WaitlistStatusResponse(token, OfferStatus.ACCEPTED, offer.getExpiresAt(), permitToken);
    }

    @Transactional
    public List<Long> handleRelease(ReleaseNotification notification) {
        List<Long> notifiedUsers = new ArrayList<>();
        int count = notification.count() == null ? 1 : notification.count();
        for (int i = 0; i < count; i++) {
            Set<String> tokens = redis.opsForZSet().range(waitlistKey(notification.eventId(), notification.zoneId()), 0, 0);
            if (tokens == null || tokens.isEmpty()) {
                break;
            }
            String token = tokens.iterator().next();
            redis.opsForZSet().remove(waitlistKey(notification.eventId(), notification.zoneId()), token);
            String activeKey = activeTokenKey(token);
            if (activeKey != null) {
                redis.delete(activeKey);
            }

            Long userId = extractUserId(token);
            if (userId == null) {
                continue;
            }

            Instant expiresAt = Instant.now().plus(Duration.ofMinutes(offerTtlMinutes));
            WaitlistOffer offer = new WaitlistOffer(
                    token, userId, notification.eventId(), notification.zoneId(),
                    OfferStatus.PENDING, expiresAt);
            offerRepository.save(offer);

            redis.opsForHash().putAll(offerKey(token), Map.of(
                    "userId", String.valueOf(userId),
                    "eventId", String.valueOf(notification.eventId()),
                    "zoneId", String.valueOf(notification.zoneId()),
                    "status", OfferStatus.PENDING.name(),
                    "expiresAt", expiresAt.toString()
            ));
            redis.expire(offerKey(token), Duration.ofMinutes(offerTtlMinutes));
            if (waitlistOffersSentCounter != null) {
                waitlistOffersSentCounter.increment();
            }
            notifiedUsers.add(userId);
        }
        return notifiedUsers;
    }

    private Long rankInWaitlist(String token) {
        Long eventId = extractEventId(token);
        Long zoneId = extractZoneId(token);
        if (eventId == null || zoneId == null) {
            return null;
        }
        return redis.opsForZSet().rank(waitlistKey(eventId, zoneId), token);
    }

    private String activeTokenKey(String token) {
        Long userId = extractUserId(token);
        Long eventId = extractEventId(token);
        Long zoneId = extractZoneId(token);
        if (userId == null || eventId == null || zoneId == null) {
            return null;
        }
        return activeTokenKey(userId, eventId, zoneId);
    }

    public String waitlistKey(Long eventId, Long zoneId) {
        return WAITLIST_PREFIX + eventId + ":" + zoneId;
    }

    public String activeTokenKey(Long userId, Long eventId, Long zoneId) {
        return ACTIVE_TOKEN_PREFIX + userId + ":" + eventId + ":" + zoneId;
    }

    public String tokenMetaKey(String token) {
        return TOKEN_META_PREFIX + token;
    }

    public String offerKey(String token) {
        return OFFER_PREFIX + token;
    }

    public String permitKey(String token) {
        return PERMIT_PREFIX + token;
    }

    private void removeFromWaitlist(Long eventId, Long zoneId, String token) {
        redis.opsForZSet().remove(waitlistKey(eventId, zoneId), token);
    }

    public Long extractUserId(String token) {
        Object userId = redis.opsForHash().get(tokenMetaKey(token), "userId");
        return userId == null ? null : Long.valueOf(userId.toString());
    }

    public Long extractEventId(String token) {
        Object eventId = redis.opsForHash().get(tokenMetaKey(token), "eventId");
        return eventId == null ? null : Long.valueOf(eventId.toString());
    }

    public Long extractZoneId(String token) {
        Object zoneId = redis.opsForHash().get(tokenMetaKey(token), "zoneId");
        return zoneId == null ? null : Long.valueOf(zoneId.toString());
    }
}
