package com.warehouse.inventory.application.service;

import com.warehouse.inventory.api.dto.CreateReservationRequest;
import com.warehouse.inventory.api.dto.ReservationItemResponse;
import com.warehouse.inventory.api.dto.ReservationResponse;
import com.warehouse.inventory.application.factory.ReservationFactory;
import com.warehouse.inventory.domain.event.ReservationCancelledEvent;
import com.warehouse.inventory.domain.event.ReservationConfirmedEvent;
import com.warehouse.inventory.domain.event.ReservationCreatedEvent;
import com.warehouse.inventory.domain.exception.ReservationNotFoundException;
import com.warehouse.inventory.domain.model.Reservation;
import com.warehouse.inventory.domain.model.ReservationAggregate;
import com.warehouse.inventory.domain.model.ReservationItem;
import com.warehouse.inventory.domain.port.EventPublisher;
import com.warehouse.inventory.domain.repository.ReservationItemRepository;
import com.warehouse.inventory.domain.repository.ReservationRepository;
import com.warehouse.inventory.domain.state.ReservationStateMachine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/**
 * Orchestrates the full reservation lifecycle.
 *
 * <p>Flow for {@link #createReservation}:
 * <ol>
 *   <li>Factory validates SKUs and builds the aggregate.</li>
 *   <li>Each line item reserves stock (with distributed lock + DB lock).</li>
 *   <li>Reservation header + items are persisted.</li>
 *   <li>Domain event is published to NATS JetStream.</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReservationService {

    private final ReservationRepository     reservationRepository;
    private final ReservationItemRepository reservationItemRepository;
    private final InventoryService          inventoryService;
    private final ReservationFactory        reservationFactory;
    private final ReservationStateMachine   stateMachine;
    private final EventPublisher            eventPublisher;

    // ─── Create ───────────────────────────────────────────────────────────────

    @Transactional
    public Mono<ReservationResponse> createReservation(CreateReservationRequest request) {
        return reservationFactory.create(request)
                // Step 1: reserve stock for all items (fails fast on insufficient stock)
                .flatMap(aggregate -> reserveAllStock(aggregate).thenReturn(aggregate))
                // Step 2: persist only after ALL stock reservations succeed
                // Split into a separate flatMap so persistAggregate() is not called
                // eagerly as a .then() argument — which would invoke repo.save() before
                // reserveAllStock even starts.
                .flatMap(this::persistAggregate)
                .flatMap(aggregate -> publishAndReturn(
                        new ReservationCreatedEvent(
                                aggregate.reservation().getId(),
                                aggregate.reservation().getOrderId()),
                        aggregate));
    }

    // ─── Confirm ──────────────────────────────────────────────────────────────

    @Transactional
    public Mono<ReservationResponse> confirmReservation(UUID id) {
        return reservationRepository.findById(id)
                .switchIfEmpty(Mono.error(new ReservationNotFoundException(id)))
                .flatMap(reservation -> {
                    Reservation confirmed = stateMachine.confirm(reservation);
                    
                    return reservationItemRepository.findByReservationId(id)
                            .collectList()
                            .flatMap(items -> {
                                if (reservation == confirmed) {
                                    // Idempotent: already confirmed. Return current state.
                                    return Mono.just(toResponse(new ReservationAggregate(reservation, items)));
                                }
                                
                                return reservationRepository.save(confirmed)
                                        .flatMap(saved -> publishAndReturn(
                                                new ReservationConfirmedEvent(saved.getId(), saved.getOrderId()),
                                                new ReservationAggregate(saved, items)));
                            });
                });
    }

    // ─── Cancel ───────────────────────────────────────────────────────────────

    @Transactional
    public Mono<ReservationResponse> cancelReservation(UUID id) {
        return reservationRepository.findById(id)
                .switchIfEmpty(Mono.error(new ReservationNotFoundException(id)))
                .flatMap(reservation ->
                        reservationItemRepository.findByReservationId(id)
                                .collectList()
                                .flatMap(items -> {
                                    Reservation cancelled = stateMachine.cancel(reservation);
                                    
                                    if (reservation == cancelled) {
                                        // Idempotent: already cancelled. Return current state.
                                        return Mono.just(toResponse(new ReservationAggregate(reservation, items)));
                                    }
                                    
                                    return releaseAllStock(items)
                                            .then(reservationRepository.save(cancelled))
                                            .flatMap(saved -> publishAndReturn(
                                                    new ReservationCancelledEvent(saved.getId(), saved.getOrderId()),
                                                    new ReservationAggregate(saved, items)));
                                }));
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    /** Reserves stock for every line item sequentially (maintains order guarantees). */
    private Mono<Void> reserveAllStock(ReservationAggregate aggregate) {
        return Flux.fromIterable(aggregate.items())
                .concatMap(item -> inventoryService.reserveStock(item.getSku(), item.getQuantity()))
                .then();
    }

    /** Releases stock for every line item sequentially on cancellation. */
    private Mono<Void> releaseAllStock(List<ReservationItem> items) {
        return Flux.fromIterable(items)
                .concatMap(item -> inventoryService.releaseStock(item.getSku(), item.getQuantity()))
                .then();
    }

    /** Persists header + items, returns aggregate with persisted IDs. */
    private Mono<ReservationAggregate> persistAggregate(ReservationAggregate aggregate) {
        return reservationRepository.save(aggregate.reservation())
                .flatMap(savedReservation ->
                        Flux.fromIterable(aggregate.items())
                                .map(item -> item.toBuilder()
                                        .reservationId(savedReservation.getId())
                                        .build())
                                .flatMap(reservationItemRepository::save)
                                .collectList()
                                .map(savedItems -> new ReservationAggregate(savedReservation, savedItems)));
    }

    /** Publishes event (fire-and-forget errors are logged, not propagated) and maps to response. */
    private <E extends com.warehouse.inventory.domain.event.DomainEvent>
    Mono<ReservationResponse> publishAndReturn(E event, ReservationAggregate aggregate) {
        return eventPublisher.publish(event)
                .doOnError(err -> log.warn("Failed to publish event {}: {}", event.getEventType(), err.getMessage()))
                .onErrorComplete()
                .thenReturn(toResponse(aggregate));
    }

    private ReservationResponse toResponse(ReservationAggregate aggregate) {
        List<ReservationItemResponse> itemResponses = aggregate.items().stream()
                .map(i -> new ReservationItemResponse(i.getId(), i.getSku(), i.getQuantity()))
                .toList();
        Reservation r = aggregate.reservation();
        return new ReservationResponse(r.getId(), r.getOrderId(), r.getStatus(),
                itemResponses, r.getCreatedAt(), r.getUpdatedAt());
    }
}
