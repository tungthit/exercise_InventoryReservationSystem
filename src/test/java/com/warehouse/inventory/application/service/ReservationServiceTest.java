package com.warehouse.inventory.application.service;

import com.warehouse.inventory.api.dto.CreateReservationRequest;
import com.warehouse.inventory.api.dto.ReservationItemRequest;
import com.warehouse.inventory.api.dto.ReservationResponse;
import com.warehouse.inventory.application.factory.ReservationFactory;
import com.warehouse.inventory.domain.event.ReservationCreatedEvent;
import com.warehouse.inventory.domain.exception.InsufficientStockException;
import com.warehouse.inventory.domain.model.*;
import com.warehouse.inventory.domain.port.EventPublisher;
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
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

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
    @Mock private EventPublisher            eventPublisher;

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
        when(reservationFactory.create(any())).thenReturn(Mono.just(aggregate));
        when(inventoryService.reserveStock("A100", 5)).thenReturn(Mono.empty());
        when(reservationRepository.save(pendingReservation)).thenReturn(Mono.just(pendingReservation));
        when(reservationItemRepository.save(item)).thenReturn(Mono.just(item));
        when(eventPublisher.publish(any())).thenReturn(Mono.empty());

        CreateReservationRequest request = new CreateReservationRequest(
                "ORD-1001", List.of(new ReservationItemRequest("A100", 5)));

        StepVerifier.create(reservationService.createReservation(request))
                .assertNext(response -> {
                    assertThat(response.orderId()).isEqualTo("ORD-1001");
                    assertThat(response.status()).isEqualTo(ReservationStatus.PENDING);
                    assertThat(response.items()).hasSize(1);
                })
                .verifyComplete();

        verify(inventoryService).reserveStock("A100", 5);
        verify(eventPublisher).publish(any(ReservationCreatedEvent.class));
    }

    @Test
    @DisplayName("createReservation propagates InsufficientStockException")
    void createReservation_insufficientStock() {
        ReservationAggregate aggregate = new ReservationAggregate(pendingReservation, List.of(item));
        when(reservationFactory.create(any())).thenReturn(Mono.just(aggregate));
        when(inventoryService.reserveStock("A100", 5))
                .thenReturn(Mono.error(new InsufficientStockException("Not enough stock")));

        CreateReservationRequest request = new CreateReservationRequest(
                "ORD-1001", List.of(new ReservationItemRequest("A100", 5)));

        StepVerifier.create(reservationService.createReservation(request))
                .expectError(InsufficientStockException.class)
                .verify();

        verify(reservationRepository, never()).save(any());
    }

    // ─── confirmReservation ───────────────────────────────────────────────────

    @Test
    @DisplayName("confirmReservation transitions PENDING -> CONFIRMED")
    void confirmReservation_success() {
        Reservation confirmed = pendingReservation.toBuilder()
                .status(ReservationStatus.CONFIRMED).build();
        when(reservationRepository.findById(reservationId)).thenReturn(Mono.just(pendingReservation));
        when(stateMachine.confirm(pendingReservation)).thenReturn(confirmed);
        when(reservationRepository.save(confirmed)).thenReturn(Mono.just(confirmed));
        when(reservationItemRepository.findByReservationId(reservationId)).thenReturn(Flux.just(item));
        when(eventPublisher.publish(any())).thenReturn(Mono.empty());

        StepVerifier.create(reservationService.confirmReservation(reservationId))
                .assertNext(response -> assertThat(response.status()).isEqualTo(ReservationStatus.CONFIRMED))
                .verifyComplete();
    }

    // ─── cancelReservation ────────────────────────────────────────────────────

    @Test
    @DisplayName("cancelReservation releases reserved stock")
    void cancelReservation_releasesStock() {
        Reservation cancelled = pendingReservation.toBuilder()
                .status(ReservationStatus.CANCELLED).build();
        when(reservationRepository.findById(reservationId)).thenReturn(Mono.just(pendingReservation));
        when(reservationItemRepository.findByReservationId(reservationId)).thenReturn(Flux.just(item));
        when(stateMachine.cancel(pendingReservation)).thenReturn(cancelled);
        when(inventoryService.releaseStock("A100", 5)).thenReturn(Mono.empty());
        when(reservationRepository.save(cancelled)).thenReturn(Mono.just(cancelled));
        when(eventPublisher.publish(any())).thenReturn(Mono.empty());

        StepVerifier.create(reservationService.cancelReservation(reservationId))
                .assertNext(response -> assertThat(response.status()).isEqualTo(ReservationStatus.CANCELLED))
                .verifyComplete();

        verify(inventoryService).releaseStock("A100", 5);
    }
}
