package com.grabmyseat.waitingroom.service;

import com.grabmyseat.waitingroom.client.InventorySaleClient;
import com.grabmyseat.waitingroom.dto.ReleaseNotification;
import com.grabmyseat.waitingroom.dto.WaitlistJoinResponse;
import com.grabmyseat.waitingroom.dto.WaitlistStatusResponse;
import com.grabmyseat.waitingroom.model.OfferStatus;
import com.grabmyseat.waitingroom.model.WaitlistOffer;
import com.grabmyseat.waitingroom.repository.WaitlistOfferRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WaitlistServiceTest {

    @Mock
    private RedisTemplate<String, String> redis;

    @Mock
    private WaitlistOfferRepository offerRepository;

    @Mock
    private InventorySaleClient inventorySaleClient;

    @InjectMocks
    private WaitlistService waitlistService;

    @Mock
    private ValueOperations<String, String> valueOps;

    @Mock
    private ZSetOperations<String, String> zSetOps;

    @Mock
    private HashOperations<String, Object, Object> hashOps;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(waitlistService, "offerTtlMinutes", 5L);
        ReflectionTestUtils.setField(waitlistService, "permitTtlMinutes", 10L);
        lenient().when(redis.opsForValue()).thenReturn(valueOps);
        lenient().when(redis.opsForZSet()).thenReturn(zSetOps);
        lenient().when(redis.opsForHash()).thenReturn(hashOps);
        lenient().doNothing().when(inventorySaleClient).requireInterestAccess(anyLong());
    }

    @Test
    void join_createsTokenAndEnqueuesUser() {
        String waitlistKey = waitlistService.waitlistKey(100L, 200L);
        when(valueOps.get(waitlistService.activeTokenKey(1L, 100L, 200L))).thenReturn(null);
        when(zSetOps.add(eq(waitlistKey), anyString(), anyDouble())).thenReturn(true);
        when(zSetOps.rank(eq(waitlistKey), anyString())).thenReturn(0L);

        WaitlistJoinResponse response = waitlistService.join(1L, 100L, 200L);

        assertThat(response.token()).isNotBlank();
        assertThat(response.position()).isZero();
        verify(zSetOps).add(eq(waitlistKey), eq(response.token()), anyDouble());
        verify(valueOps).set(eq(waitlistService.activeTokenKey(1L, 100L, 200L)), eq(response.token()), anyLong(), any());
        verify(hashOps).putAll(eq(waitlistService.tokenMetaKey(response.token())), any(Map.class));
    }

    @Test
    void join_existingActiveTokenReplacesOldToken() {
        String oldToken = "old-token";
        when(valueOps.get(waitlistService.activeTokenKey(1L, 100L, 200L))).thenReturn(oldToken);
        when(zSetOps.add(eq(waitlistService.waitlistKey(100L, 200L)), anyString(), anyDouble())).thenReturn(true);
        when(zSetOps.rank(eq(waitlistService.waitlistKey(100L, 200L)), anyString())).thenReturn(0L);
        when(offerRepository.findByToken(oldToken)).thenReturn(Optional.empty());

        WaitlistJoinResponse response = waitlistService.join(1L, 100L, 200L);

        assertThat(response.token()).isNotBlank().isNotEqualTo(oldToken);
        verify(zSetOps).remove(waitlistService.waitlistKey(100L, 200L), oldToken);
        verify(redis).delete(waitlistService.tokenMetaKey(oldToken));
    }

    @Test
    void status_waitingReturnsNullStatus() {
        String token = "token-1";
        when(offerRepository.findByToken(token)).thenReturn(Optional.empty());
        when(hashOps.get(waitlistService.tokenMetaKey(token), "eventId")).thenReturn("100");
        when(hashOps.get(waitlistService.tokenMetaKey(token), "zoneId")).thenReturn("200");
        when(zSetOps.rank(waitlistService.waitlistKey(100L, 200L), token)).thenReturn(3L);

        WaitlistStatusResponse status = waitlistService.status(token);

        assertThat(status.status()).isNull();
        assertThat(status.offerExpiresAt()).isNull();
        assertThat(status.permitToken()).isNull();
    }

    @Test
    void status_pendingOfferReturnsDetails() {
        String token = "token-1";
        Instant expiresAt = Instant.now().plus(Duration.ofMinutes(5));
        WaitlistOffer offer = new WaitlistOffer(token, 1L, 100L, 200L, OfferStatus.PENDING, expiresAt);
        when(offerRepository.findByToken(token)).thenReturn(Optional.of(offer));

        WaitlistStatusResponse status = waitlistService.status(token);

        assertThat(status.status()).isEqualTo(OfferStatus.PENDING);
        assertThat(status.offerExpiresAt()).isEqualTo(expiresAt);
        assertThat(status.permitToken()).isNull();
    }

    @Test
    void status_acceptedOfferReturnsPermitToken() {
        String token = "token-1";
        Instant expiresAt = Instant.now().plus(Duration.ofMinutes(5));
        WaitlistOffer offer = new WaitlistOffer(token, 1L, 100L, 200L, OfferStatus.ACCEPTED, expiresAt);
        when(offerRepository.findByToken(token)).thenReturn(Optional.of(offer));
        when(valueOps.get(waitlistService.permitKey(token))).thenReturn("permit-token");

        WaitlistStatusResponse status = waitlistService.status(token);

        assertThat(status.status()).isEqualTo(OfferStatus.ACCEPTED);
        assertThat(status.permitToken()).isEqualTo("permit-token");
    }

    @Test
    void accept_pendingOfferWithinWindowReturnsPermit() {
        String token = "token-1";
        Instant expiresAt = Instant.now().plus(Duration.ofMinutes(5));
        WaitlistOffer offer = new WaitlistOffer(token, 1L, 100L, 200L, OfferStatus.PENDING, expiresAt);
        when(offerRepository.findByToken(token)).thenReturn(Optional.of(offer));

        WaitlistStatusResponse status = waitlistService.accept(token);

        assertThat(status.status()).isEqualTo(OfferStatus.ACCEPTED);
        assertThat(status.permitToken()).isNotBlank();
        assertThat(offer.getStatus()).isEqualTo(OfferStatus.ACCEPTED);
        verify(valueOps).set(eq(waitlistService.permitKey(token)), anyString(), eq(10L), any());
    }

    @Test
    void accept_expiredOfferThrows() {
        String token = "token-1";
        Instant expiresAt = Instant.now().minus(Duration.ofMinutes(1));
        WaitlistOffer offer = new WaitlistOffer(token, 1L, 100L, 200L, OfferStatus.PENDING, expiresAt);
        when(offerRepository.findByToken(token)).thenReturn(Optional.of(offer));

        assertThatThrownBy(() -> waitlistService.accept(token))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("offer expired");
        assertThat(offer.getStatus()).isEqualTo(OfferStatus.EXPIRED);
    }

    @Test
    void handleRelease_popsNextUserAndCreatesOffer() {
        String token = "token-1";
        when(zSetOps.range(waitlistService.waitlistKey(100L, 200L), 0, 0)).thenReturn(Set.of(token));
        when(hashOps.get(waitlistService.tokenMetaKey(token), "userId")).thenReturn("42");
        when(offerRepository.save(any(WaitlistOffer.class))).thenAnswer(inv -> inv.getArgument(0));

        ReleaseNotification notification = new ReleaseNotification(100L, 200L, 1);
        var notified = waitlistService.handleRelease(notification);

        assertThat(notified).containsExactly(42L);
        verify(zSetOps).remove(waitlistService.waitlistKey(100L, 200L), token);
        ArgumentCaptor<WaitlistOffer> offerCaptor = ArgumentCaptor.forClass(WaitlistOffer.class);
        verify(offerRepository).save(offerCaptor.capture());
        WaitlistOffer saved = offerCaptor.getValue();
        assertThat(saved.getStatus()).isEqualTo(OfferStatus.PENDING);
        assertThat(saved.getUserId()).isEqualTo(42L);
        assertThat(saved.getEventId()).isEqualTo(100L);
        assertThat(saved.getZoneId()).isEqualTo(200L);
    }

    @Test
    void handleRelease_missingMetadataSkipsEntry() {
        String token = "token-1";
        when(zSetOps.range(waitlistService.waitlistKey(100L, 200L), 0, 0)).thenReturn(Set.of(token));
        when(hashOps.get(waitlistService.tokenMetaKey(token), "userId")).thenReturn(null);

        ReleaseNotification notification = new ReleaseNotification(100L, 200L, 1);
        var notified = waitlistService.handleRelease(notification);

        assertThat(notified).isEmpty();
    }
}
