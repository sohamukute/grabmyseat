package com.grabmyseat.saga.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grabmyseat.saga.client.ClientException;
import com.grabmyseat.saga.client.InventoryClient;
import com.grabmyseat.saga.client.TicketingClient;
import com.grabmyseat.saga.client.WaitingRoomClient;
import com.grabmyseat.saga.client.WalletClient;
import com.grabmyseat.saga.dto.EventDto;
import com.grabmyseat.saga.dto.LedgerEntryDto;
import com.grabmyseat.saga.dto.ReservationDto;
import com.grabmyseat.saga.dto.ZoneDto;
import com.grabmyseat.saga.model.SagaInstance;
import com.grabmyseat.saga.model.SagaOutbox;
import com.grabmyseat.saga.model.SagaStatus;
import com.grabmyseat.saga.model.SagaStep;
import com.grabmyseat.saga.repository.SagaInstanceRepository;
import com.grabmyseat.saga.repository.SagaOutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatusCode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SagaServiceTest {

    @Mock
    private SagaInstanceRepository sagaInstanceRepository;

    @Mock
    private SagaOutboxRepository sagaOutboxRepository;

    @Mock
    private InventoryClient inventoryClient;

    @Mock
    private WalletClient walletClient;

    @Mock
    private WaitingRoomClient waitingRoomClient;

    @Mock
    private TicketingClient ticketingClient;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private SagaService sagaService;

    @BeforeEach
    void setUp() {
        sagaService = new SagaService(sagaInstanceRepository, sagaOutboxRepository,
                inventoryClient, walletClient, Optional.of(waitingRoomClient), ticketingClient, objectMapper);
    }

    @Test
    void confirmBooking_happyPath_debitsWalletAndConfirmsReservation() {
        String token = "res-123";
        Long userId = 42L;
        Long eventId = 1L;
        Long zoneId = 10L;
        List<Long> seatIds = List.of(100L, 101L);
        BigDecimal price = new BigDecimal("50.00");
        BigDecimal totalAmount = new BigDecimal("100.00");
        Instant expiresAt = Instant.now().plusSeconds(300);

        ReservationDto reservation = new ReservationDto(token, eventId, zoneId, seatIds, "HELD", expiresAt, totalAmount);
        SagaInstance saga = new SagaInstance(token, userId, eventId, zoneId, seatIds, totalAmount,
                SagaStatus.STARTED, expiresAt);
        saga.setStatus(SagaStatus.STARTED);

        when(sagaInstanceRepository.findByReservationToken(token)).thenReturn(Optional.empty());
        when(inventoryClient.getReservation(token)).thenReturn(reservation);
        when(sagaInstanceRepository.save(any(SagaInstance.class))).thenReturn(saga);
        when(sagaOutboxRepository.save(any(SagaOutbox.class))).thenAnswer(inv -> inv.getArgument(0));
        when(walletClient.debit(userId, totalAmount, token, token))
                .thenReturn(new LedgerEntryDto(1L, 1L, "DEBIT", totalAmount, new BigDecimal("900.00"), token, token));

        SagaInstance result = sagaService.confirmBooking(userId, token);

        assertThat(result.getStatus()).isEqualTo(SagaStatus.CONFIRMED);
        verify(inventoryClient).confirmReservation(token, userId);
        verify(walletClient, never()).credit(any(), any(), any(), any());
    }

    @Test
    void confirmBooking_insufficientFunds_setsFailedAndThrows() {
        String token = "res-123";
        Long userId = 42L;
        Long eventId = 1L;
        Long zoneId = 10L;
        List<Long> seatIds = List.of(100L);
        BigDecimal totalAmount = new BigDecimal("50.00");
        Instant expiresAt = Instant.now().plusSeconds(300);

        ReservationDto reservation = new ReservationDto(token, eventId, zoneId, seatIds, "HELD", expiresAt, totalAmount);
        SagaInstance saga = new SagaInstance(token, userId, eventId, zoneId, seatIds, totalAmount,
                SagaStatus.STARTED, expiresAt);

        when(sagaInstanceRepository.findByReservationToken(token)).thenReturn(Optional.empty());
        when(inventoryClient.getReservation(token)).thenReturn(reservation);
        when(sagaInstanceRepository.save(any(SagaInstance.class))).thenReturn(saga);
        when(sagaOutboxRepository.save(any(SagaOutbox.class))).thenAnswer(inv -> inv.getArgument(0));
        when(walletClient.debit(userId, totalAmount, token, token))
                .thenThrow(new ClientException(HttpStatusCode.valueOf(409),
                        "{\"error\":\"INSUFFICIENT_FUNDS\"}"));

        assertThatThrownBy(() -> sagaService.confirmBooking(userId, token))
                .isInstanceOf(SagaException.class)
                .satisfies(ex -> {
                    assertThat(((SagaException) ex).getCode())
                            .isEqualTo(SagaException.Code.INSUFFICIENT_FUNDS);
                });

        assertThat(saga.getStatus()).isEqualTo(SagaStatus.FAILED);
        verify(inventoryClient, never()).confirmReservation(any(), any());
        verify(walletClient, never()).credit(any(), any(), any(), any());
    }

    @Test
    void confirmBooking_duplicateDebit_treatedAsDebitedAndProceedsToConfirm() {
        String token = "res-123";
        Long userId = 42L;
        Long eventId = 1L;
        Long zoneId = 10L;
        List<Long> seatIds = List.of(100L);
        BigDecimal totalAmount = new BigDecimal("50.00");
        Instant expiresAt = Instant.now().plusSeconds(300);

        ReservationDto reservation = new ReservationDto(token, eventId, zoneId, seatIds, "HELD", expiresAt, totalAmount);
        SagaInstance saga = new SagaInstance(token, userId, eventId, zoneId, seatIds, totalAmount,
                SagaStatus.STARTED, expiresAt);

        when(sagaInstanceRepository.findByReservationToken(token)).thenReturn(Optional.empty());
        when(inventoryClient.getReservation(token)).thenReturn(reservation);
        when(sagaInstanceRepository.save(any(SagaInstance.class))).thenReturn(saga);
        when(sagaOutboxRepository.save(any(SagaOutbox.class))).thenAnswer(inv -> inv.getArgument(0));
        when(walletClient.debit(userId, totalAmount, token, token))
                .thenThrow(new ClientException(HttpStatusCode.valueOf(409),
                        "{\"error\":\"DUPLICATE_IDEMPOTENCY_KEY\"}"));

        SagaInstance result = sagaService.confirmBooking(userId, token);

        assertThat(result.getStatus()).isEqualTo(SagaStatus.CONFIRMED);
        verify(inventoryClient).confirmReservation(token, userId);
        verify(walletClient, never()).credit(any(), any(), any(), any());

        ArgumentCaptor<SagaOutbox> outboxCaptor = ArgumentCaptor.forClass(SagaOutbox.class);
        verify(sagaOutboxRepository, times(5)).save(outboxCaptor.capture());
        assertThat(outboxCaptor.getAllValues().get(1).getStep()).isEqualTo(SagaStep.DEBIT_DUPLICATE);
        assertThat(outboxCaptor.getAllValues().get(4).getStep()).isEqualTo(SagaStep.TICKET_ISSUED);
    }

    @Test
    void confirmBooking_confirmFailsAfterDebit_compensatesWithRefundAndRelease() {
        String token = "res-123";
        Long userId = 42L;
        Long eventId = 1L;
        Long zoneId = 10L;
        List<Long> seatIds = List.of(100L);
        BigDecimal totalAmount = new BigDecimal("50.00");
        Instant expiresAt = Instant.now().plusSeconds(300);

        ReservationDto reservation = new ReservationDto(token, eventId, zoneId, seatIds, "HELD", expiresAt, totalAmount);
        SagaInstance saga = new SagaInstance(token, userId, eventId, zoneId, seatIds, totalAmount,
                SagaStatus.STARTED, expiresAt);

        when(sagaInstanceRepository.findByReservationToken(token)).thenReturn(Optional.empty());
        when(inventoryClient.getReservation(token)).thenReturn(reservation);
        when(sagaInstanceRepository.save(any(SagaInstance.class))).thenReturn(saga);
        when(sagaOutboxRepository.save(any(SagaOutbox.class))).thenAnswer(inv -> inv.getArgument(0));
        when(walletClient.debit(userId, totalAmount, token, token))
                .thenReturn(new LedgerEntryDto(1L, 1L, "DEBIT", totalAmount, new BigDecimal("900.00"), token, token));
        doThrow(new ClientException(HttpStatusCode.valueOf(500), "inventory error"))
                .when(inventoryClient).confirmReservation(token, userId);

        SagaInstance result = sagaService.confirmBooking(userId, token);

        assertThat(result.getStatus()).isEqualTo(SagaStatus.COMPENSATED);
        verify(walletClient).credit(userId, totalAmount, token + "-refund", token);
        verify(inventoryClient).cancelReservation(token, userId);
    }

    @Test
    void cancelBooking_confirmed_compensatesAndReturnsCompensated() {
        String token = "res-123";
        Long userId = 42L;
        Long eventId = 1L;
        Long zoneId = 10L;
        List<Long> seatIds = List.of(100L);
        BigDecimal totalAmount = new BigDecimal("50.00");
        Instant expiresAt = Instant.now().plusSeconds(300);

        SagaInstance saga = new SagaInstance(token, userId, eventId, zoneId, seatIds, totalAmount,
                SagaStatus.CONFIRMED, expiresAt);

        when(sagaInstanceRepository.findByReservationToken(token)).thenReturn(Optional.of(saga));
        when(sagaOutboxRepository.save(any(SagaOutbox.class))).thenAnswer(inv -> inv.getArgument(0));
        when(walletClient.credit(userId, totalAmount, token + "-refund", token))
                .thenReturn(new LedgerEntryDto(2L, 1L, "CREDIT", totalAmount, new BigDecimal("950.00"),
                        token + "-refund", token));

        SagaInstance result = sagaService.cancelBooking(userId, token);

        assertThat(result.getStatus()).isEqualTo(SagaStatus.COMPENSATED);
        verify(walletClient).credit(userId, totalAmount, token + "-refund", token);
        verify(inventoryClient).cancelReservation(token, userId);
    }

    @Test
    void confirmBooking_returnsExistingSagaWhenAlreadyStarted() {
        String token = "res-123";
        Long userId = 42L;
        SagaInstance existing = new SagaInstance(token, userId, 1L, 1L, List.of(), new BigDecimal("10.00"),
                SagaStatus.CONFIRMED, Instant.now().plusSeconds(300));

        when(sagaInstanceRepository.findByReservationToken(token)).thenReturn(Optional.of(existing));

        SagaInstance result = sagaService.confirmBooking(userId, token);

        assertThat(result).isEqualTo(existing);
        verify(inventoryClient, never()).getReservation(any());
    }

    @Test
    void cancelBooking_notOwner_throwsNotOwner() {
        String token = "res-123";
        SagaInstance saga = new SagaInstance(token, 42L, 1L, 1L, List.of(), new BigDecimal("10.00"),
                SagaStatus.CONFIRMED, Instant.now().plusSeconds(300));

        when(sagaInstanceRepository.findByReservationToken(token)).thenReturn(Optional.of(saga));

        assertThatThrownBy(() -> sagaService.cancelBooking(99L, token))
                .isInstanceOf(SagaException.class)
                .satisfies(ex -> {
                    assertThat(((SagaException) ex).getCode())
                            .isEqualTo(SagaException.Code.NOT_OWNER);
                });
    }

    @Test
    void confirmBooking_walletUnreachable_debitFailsAndThrowsPaymentError() {
        String token = "res-123";
        Long userId = 42L;
        Long eventId = 1L;
        Long zoneId = 10L;
        List<Long> seatIds = List.of(100L);
        BigDecimal totalAmount = new BigDecimal("50.00");
        Instant expiresAt = Instant.now().plusSeconds(300);

        ReservationDto reservation = new ReservationDto(token, eventId, zoneId, seatIds, "HELD", expiresAt, totalAmount);
        SagaInstance saga = new SagaInstance(token, userId, eventId, zoneId, seatIds, totalAmount,
                SagaStatus.STARTED, expiresAt);

        when(sagaInstanceRepository.findByReservationToken(token)).thenReturn(Optional.empty());
        when(inventoryClient.getReservation(token)).thenReturn(reservation);
        when(sagaInstanceRepository.save(any(SagaInstance.class))).thenReturn(saga);
        when(sagaOutboxRepository.save(any(SagaOutbox.class))).thenAnswer(inv -> inv.getArgument(0));
        when(walletClient.debit(userId, totalAmount, token, token))
                .thenThrow(new RuntimeException("connection refused"));

        assertThatThrownBy(() -> sagaService.confirmBooking(userId, token))
                .isInstanceOf(SagaException.class)
                .satisfies(ex -> {
                    assertThat(((SagaException) ex).getCode())
                            .isEqualTo(SagaException.Code.PAYMENT_ERROR);
                });

        assertThat(saga.getStatus()).isEqualTo(SagaStatus.FAILED);
        verify(inventoryClient, never()).confirmReservation(any(), any());
        verify(walletClient, never()).credit(any(), any(), any(), any());
    }

    @Test
    void confirmBooking_inventoryUnreachableAfterDebit_compensatesWithRefundAndRelease() {
        String token = "res-123";
        Long userId = 42L;
        Long eventId = 1L;
        Long zoneId = 10L;
        List<Long> seatIds = List.of(100L);
        BigDecimal totalAmount = new BigDecimal("50.00");
        Instant expiresAt = Instant.now().plusSeconds(300);

        ReservationDto reservation = new ReservationDto(token, eventId, zoneId, seatIds, "HELD", expiresAt, totalAmount);
        SagaInstance saga = new SagaInstance(token, userId, eventId, zoneId, seatIds, totalAmount,
                SagaStatus.STARTED, expiresAt);

        when(sagaInstanceRepository.findByReservationToken(token)).thenReturn(Optional.empty());
        when(inventoryClient.getReservation(token)).thenReturn(reservation);
        when(sagaInstanceRepository.save(any(SagaInstance.class))).thenReturn(saga);
        when(sagaOutboxRepository.save(any(SagaOutbox.class))).thenAnswer(inv -> inv.getArgument(0));
        when(walletClient.debit(userId, totalAmount, token, token))
                .thenReturn(new LedgerEntryDto(1L, 1L, "DEBIT", totalAmount, new BigDecimal("900.00"), token, token));
        doThrow(new RuntimeException("inventory down"))
                .when(inventoryClient).confirmReservation(token, userId);

        SagaInstance result = sagaService.confirmBooking(userId, token);

        assertThat(result.getStatus()).isEqualTo(SagaStatus.COMPENSATED);
        verify(walletClient).credit(userId, totalAmount, token + "-refund", token);
        verify(inventoryClient).cancelReservation(token, userId);
    }
}
