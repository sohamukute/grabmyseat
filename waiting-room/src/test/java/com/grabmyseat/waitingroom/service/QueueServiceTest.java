package com.grabmyseat.waitingroom.service;

import com.grabmyseat.waitingroom.RedisKeys;
import com.grabmyseat.waitingroom.client.InventorySaleClient;
import com.grabmyseat.waitingroom.dto.JoinQueueResponse;
import com.grabmyseat.waitingroom.dto.QueuePositionResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QueueServiceTest {

    @Mock
    private RedisTemplate<String, String> redis;

    @Mock
    private AdmissionService admissionService;

    @Mock
    private InventorySaleClient inventorySaleClient;

    @InjectMocks
    private QueueService queueService;

    @Mock
    private ValueOperations<String, String> valueOps;

    @Mock
    private ZSetOperations<String, String> zSetOps;

    @Mock
    private HashOperations<String, Object, Object> hashOps;

    @Mock
    private SetOperations<String, String> setOps;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(queueService, "queueTokenTtlHours", 2L);
        lenient().when(redis.opsForValue()).thenReturn(valueOps);
        lenient().when(redis.opsForZSet()).thenReturn(zSetOps);
        lenient().when(redis.opsForHash()).thenReturn(hashOps);
        lenient().when(redis.opsForSet()).thenReturn(setOps);
        lenient().doNothing().when(inventorySaleClient).requireQueueAccess(anyLong());
    }

    @Test
    void join_createsTokenAndEnqueuesUser() {
        String queueKey = queueService.queueKey(100L);
        when(valueOps.get(queueService.activeTokenKey(1L, 100L))).thenReturn(null);
        when(zSetOps.add(eq(queueKey), anyString(), anyDouble())).thenReturn(true);
        when(zSetOps.rank(eq(queueKey), anyString())).thenReturn(0L);
        when(admissionService.estimateWait(0L, 100L)).thenReturn(0L);

        JoinQueueResponse response = queueService.join(1L, 100L);

        assertThat(response.token()).isNotBlank();
        assertThat(response.position()).isZero();
        assertThat(response.estimatedWaitSeconds()).isZero();
        verify(zSetOps).add(eq(queueKey), eq(response.token()), anyDouble());
        verify(valueOps).set(eq(queueService.activeTokenKey(1L, 100L)), eq(response.token()), anyLong(), any());
        verify(hashOps).putAll(eq(queueService.tokenMetaKey(response.token())), any(Map.class));
        verify(setOps).add(RedisKeys.ACTIVE_EVENTS_KEY, "100");
    }

    @Test
    void join_existingActiveTokenReplacesOldToken() {
        String oldToken = "old-token";
        when(valueOps.get(queueService.activeTokenKey(1L, 100L))).thenReturn(oldToken);
        when(zSetOps.add(eq(queueService.queueKey(100L)), anyString(), anyDouble())).thenReturn(true);
        when(zSetOps.rank(eq(queueService.queueKey(100L)), anyString())).thenReturn(0L);
        when(admissionService.estimateWait(0L, 100L)).thenReturn(0L);

        JoinQueueResponse response = queueService.join(1L, 100L);

        assertThat(response.token()).isNotBlank().isNotEqualTo(oldToken);
        verify(zSetOps).remove(queueService.queueKey(100L), oldToken);
        verify(redis).delete(queueService.tokenMetaKey(oldToken));
    }

    @Test
    void position_returnsWaitingStatus() {
        String token = "token-1";
        when(valueOps.get(queueService.permitKey(token))).thenReturn(null);
        when(zSetOps.rank(queueService.queueKey(100L), token)).thenReturn(3L);

        QueuePositionResponse position = queueService.position(100L, token);

        assertThat(position.status()).isEqualTo("WAITING");
        assertThat(position.position()).isEqualTo(3L);
    }

    @Test
    void position_returnsAdmittedStatusWhenPermitExists() {
        String token = "token-1";
        when(valueOps.get(queueService.permitKey(token))).thenReturn("permit-token");

        QueuePositionResponse position = queueService.position(100L, token);

        assertThat(position.status()).isEqualTo("ADMITTED");
        assertThat(position.position()).isZero();
    }

    @Test
    void position_returnsUnknownForMissingToken() {
        String token = "missing";
        when(valueOps.get(queueService.permitKey(token))).thenReturn(null);
        when(zSetOps.rank(queueService.queueKey(100L), token)).thenReturn(null);

        QueuePositionResponse position = queueService.position(100L, token);

        assertThat(position.status()).isEqualTo("UNKNOWN");
        assertThat(position.position()).isEqualTo(-1L);
    }

    @Test
    void leave_removesTokenFromQueueAndMetadata() {
        String token = "token-1";
        when(hashOps.get(queueService.tokenMetaKey(token), "userId")).thenReturn("1");

        queueService.leave(100L, token);

        verify(zSetOps).remove(queueService.queueKey(100L), token);
        verify(redis).delete(queueService.permitKey(token));
        verify(redis).delete(queueService.activeTokenKey(1L, 100L));
        verify(redis).delete(queueService.tokenMetaKey(token));
    }

    @Test
    void leave_missingMetadataStillRemovesFromQueue() {
        String token = "token-1";
        when(hashOps.get(queueService.tokenMetaKey(token), "userId")).thenReturn(null);

        queueService.leave(100L, token);

        verify(zSetOps).remove(queueService.queueKey(100L), token);
        ArgumentCaptor<String> deletedKeys = ArgumentCaptor.forClass(String.class);
        verify(redis, times(2)).delete(deletedKeys.capture());
        assertThat(deletedKeys.getAllValues()).containsExactlyInAnyOrder(
                queueService.permitKey(token),
                queueService.tokenMetaKey(token));
    }
}
