package com.warehouse.inventory.application.service;

import com.warehouse.inventory.api.dto.CreateReservationRequest;
import com.warehouse.inventory.api.dto.ReservationItemRequest;
import com.warehouse.inventory.api.dto.ReservationResponse;
import com.warehouse.inventory.application.factory.ReservationFactory;
import com.warehouse.inventory.domain.event.ReservationCreatedEvent;
import com.warehouse.inventory.domain.exception.InsufficientStockException;
import com.warehouse.inventory.domain.model.*;
import com.warehouse.inventory.domain.repository.OutboxEventRepository;
import com.warehouse.inventory.domain.repository.ReservationItemRepository;
import com.warehouse.inventory.domain.repository.ReservationRepository;
import com.warehouse.inventory.domain.state.ReservationStateMachine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;


import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReservationService")
class ReservationServiceTest {

    @Mock private ReservationRepository     reservationRepository;
    @Mock private ReservationItemRepository reservationItemRepository;
    @Mock private InventoryService          inventoryService;
    @Mock private ReservationFactory        reservationFactory;
    @Mock private ReservationStateMachine   stateMachine;
    @Mock private OutboxEventRepository     outboxEventRepository;
    @Mock private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @InjectMocks
    private ReservationService reservationService;

    private UUID reservationId;
    private Reservation pendingReservation;
    private ReservationItem item;

    @BeforeEach
    void setUp() {
        reservationId = UUID.randomUUID();
        pendingReservation = Reservation.builder()
                .id(reservationId)
                .orderId("ORD-1001")
                .status(ReservationStatus.PENDING)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        item = ReservationItem.builder()
                .id(UUID.randomUUID())
                .reservationId(reservationId)
                .sku("A100")
                .quantity(5)
                .build();
    }

    // ─── createReservation ────────────────────────────────────────────────────

    @Test
    @DisplayName("createReservation succeeds on happy path")
    void createReservation_success() {
        ReservationAggregate aggregate = new ReservationAggregate(pendingReservation, List.of(item));
        when(reservationFactory.create(any())).thenReturn(aggregate);
        // inventoryService.reserveStock is void, so default mock behavior is success
        when(reservationRepository.save(pendingReservation)).thenReturn(pendingReservation);
        when(reservationItemRepository.save(item)).thenReturn(item);
        try {
            when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        } catch (Exception e) {}
        when(outboxEventRepository.save(any())).thenReturn(new OutboxEvent());

        CreateReservationRequest request = new CreateReservationRequest(
                "ORD-1001", List.of(new ReservationItemRequest("A100", 5)));

        ReservationResponse response = reservationService.createReservation(request);

        assertThat(response.orderId()).isEqualTo("ORD-1001");
        assertThat(response.status()).isEqualTo(ReservationStatus.PENDING);
        assertThat(response.items()).hasSize(1);

        verify(inventoryService).reserveStock("A100", 5);
        verify(outboxEventRepository).save(any(OutboxEvent.class));
    }

    @Test
    @DisplayName("createReservation propagates InsufficientStockException")
    void createReservation_insufficientStock() {
        ReservationAggregate aggregate = new ReservationAggregate(pendingReservation, List.of(item));
        when(reservationFactory.create(any())).thenReturn(aggregate);
        doThrow(new InsufficientStockException("Not enough stock"))
                .when(inventoryService).reserveStock("A100", 5);

        CreateReservationRequest request = new CreateReservationRequest(
                "ORD-1001", List.of(new ReservationItemRequest("A100", 5)));

        org.junit.jupiter.api.Assertions.assertThrows(InsufficientStockException.class, () ->
            reservationService.createReservation(request)
        );

        verify(reservationRepository, never()).save(any());
    }

    // ─── confirmReservation ───────────────────────────────────────────────────

    @Test
    @DisplayName("confirmReservation transitions PENDING -> CONFIRMED")
    void confirmReservation_success() {
        Reservation confirmed = pendingReservation.toBuilder()
                .status(ReservationStatus.CONFIRMED).build();
        when(reservationRepository.findById(reservationId)).thenReturn(java.util.Optional.of(pendingReservation));
        when(stateMachine.confirm(pendingReservation)).thenReturn(confirmed);
        when(reservationRepository.save(confirmed)).thenReturn(confirmed);
        when(reservationItemRepository.findByReservationId(reservationId)).thenReturn(List.of(item));
        try {
            when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        } catch (Exception e) {}
        when(outboxEventRepository.save(any())).thenReturn(new OutboxEvent());

        ReservationResponse response = reservationService.confirmReservation(reservationId);
        
        assertThat(response.status()).isEqualTo(ReservationStatus.CONFIRMED);
    }

    // ─── cancelReservation ────────────────────────────────────────────────────

    @Test
    @DisplayName("cancelReservation releases reserved stock")
    void cancelReservation_releasesStock() {
        Reservation cancelled = pendingReservation.toBuilder()
                .status(ReservationStatus.CANCELLED).build();
        when(reservationRepository.findById(reservationId)).thenReturn(java.util.Optional.of(pendingReservation));
        when(reservationItemRepository.findByReservationId(reservationId)).thenReturn(List.of(item));
        when(stateMachine.cancel(pendingReservation)).thenReturn(cancelled);
        // releaseStock is void
        when(reservationRepository.save(cancelled)).thenReturn(cancelled);
        try {
            when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        } catch (Exception e) {}
        when(outboxEventRepository.save(any())).thenReturn(new OutboxEvent());

        ReservationResponse response = reservationService.cancelReservation(reservationId);
        
        assertThat(response.status()).isEqualTo(ReservationStatus.CANCELLED);

        verify(inventoryService).releaseStock("A100", 5);
    }

    @Test
    @DisplayName("cancelReservation throws ReservationNotFoundException if ID not found")
    void cancelReservation_notFound() {
        when(reservationRepository.findById(reservationId)).thenReturn(java.util.Optional.empty());

        org.junit.jupiter.api.Assertions.assertThrows(com.warehouse.inventory.domain.exception.ReservationNotFoundException.class, () ->
            reservationService.cancelReservation(reservationId)
        );
    }

    @Test
    @DisplayName("confirmReservation throws ReservationNotFoundException if ID not found")
    void confirmReservation_notFound() {
        when(reservationRepository.findById(reservationId)).thenReturn(java.util.Optional.empty());

        org.junit.jupiter.api.Assertions.assertThrows(com.warehouse.inventory.domain.exception.ReservationNotFoundException.class, () ->
            reservationService.confirmReservation(reservationId)
        );
    }

    @Test
    @DisplayName("confirmReservation propagates InvalidStateTransitionException")
    void confirmReservation_invalidState() {
        when(reservationRepository.findById(reservationId)).thenReturn(java.util.Optional.of(pendingReservation));
        when(stateMachine.confirm(pendingReservation)).thenThrow(
                new com.warehouse.inventory.domain.exception.InvalidStateTransitionException(ReservationStatus.CONFIRMED, "confirm"));

        org.junit.jupiter.api.Assertions.assertThrows(com.warehouse.inventory.domain.exception.InvalidStateTransitionException.class, () ->
            reservationService.confirmReservation(reservationId)
        );
    }

    @Test
    @DisplayName("createReservation with multiple items fails fast if second item lacks stock")
    void createReservation_partialFailure() {
        ReservationItem item2 = ReservationItem.builder()
                .id(UUID.randomUUID()).reservationId(reservationId).sku("B200").quantity(10).build();
        ReservationAggregate aggregate = new ReservationAggregate(pendingReservation, List.of(item, item2));

        when(reservationFactory.create(any())).thenReturn(aggregate);

        // First item succeeds (do nothing)
        // Second item fails
        lenient().doThrow(new InsufficientStockException("Not enough B200"))
            .when(inventoryService).reserveStock("B200", 10);

        CreateReservationRequest request = new CreateReservationRequest(
                "ORD-1001", List.of(new ReservationItemRequest("A100", 5), new ReservationItemRequest("B200", 10)));

        Throwable t = org.junit.jupiter.api.Assertions.assertThrows(InsufficientStockException.class, () ->
            reservationService.createReservation(request)
        );
        assertThat(t.getMessage()).contains("B200");

        // Ensure we don't save the aggregate if creating fails halfway
        verify(reservationRepository, never()).save(any());
    }
}
