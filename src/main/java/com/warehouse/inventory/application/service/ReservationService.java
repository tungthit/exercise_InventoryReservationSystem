package com.warehouse.inventory.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.warehouse.inventory.api.dto.CreateReservationRequest;
import com.warehouse.inventory.api.dto.ReservationItemResponse;
import com.warehouse.inventory.api.dto.ReservationResponse;
import com.warehouse.inventory.application.factory.ReservationFactory;
import com.warehouse.inventory.domain.event.ReservationCancelledEvent;
import com.warehouse.inventory.domain.event.ReservationConfirmedEvent;
import com.warehouse.inventory.domain.event.ReservationCreatedEvent;
import com.warehouse.inventory.domain.exception.ReservationNotFoundException;
import com.warehouse.inventory.domain.model.OutboxEvent;
import com.warehouse.inventory.domain.model.Reservation;
import com.warehouse.inventory.domain.model.ReservationAggregate;
import com.warehouse.inventory.domain.model.ReservationItem;
import com.warehouse.inventory.domain.repository.OutboxEventRepository;
import com.warehouse.inventory.domain.repository.ReservationItemRepository;
import com.warehouse.inventory.domain.repository.ReservationRepository;
import com.warehouse.inventory.domain.state.ReservationStateMachine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import java.time.Instant;
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
    private final OutboxEventRepository     outboxEventRepository;
    private final InventoryService          inventoryService;
    private final ReservationFactory        reservationFactory;
    private final ReservationStateMachine   stateMachine;
    private final ObjectMapper              objectMapper;

    // ─── Create ───────────────────────────────────────────────────────────────

    @Transactional
    public ReservationResponse createReservation(CreateReservationRequest request) {
        ReservationAggregate aggregate = reservationFactory.create(request);

        // Step 1: reserve stock for all items (fails fast on insufficient stock)
        reserveAllStock(aggregate);

        // Step 2: persist only after ALL stock reservations succeed
        ReservationAggregate savedAggregate = persistAggregate(aggregate);

        return publishAndReturn(
                new ReservationCreatedEvent(
                        savedAggregate.reservation().getId(),
                        savedAggregate.reservation().getOrderId()),
                savedAggregate);
    }

    // ─── Confirm ──────────────────────────────────────────────────────────────

    @Transactional
    public ReservationResponse confirmReservation(UUID id) {
        Reservation reservation = reservationRepository.findById(id)
                .orElseThrow(() -> new ReservationNotFoundException(id));

        Reservation confirmed = stateMachine.confirm(reservation);
        List<ReservationItem> items = reservationItemRepository.findByReservationId(id);

        if (reservation == confirmed) {
            // Idempotent: already confirmed. Return current state.
            return toResponse(new ReservationAggregate(reservation, items));
        }
        
        Reservation saved = reservationRepository.save(confirmed);
        return publishAndReturn(
                new ReservationConfirmedEvent(saved.getId(), saved.getOrderId()),
                new ReservationAggregate(saved, items));
    }

    // ─── Cancel ───────────────────────────────────────────────────────────────

    @Transactional
    public ReservationResponse cancelReservation(UUID id) {
        Reservation reservation = reservationRepository.findById(id)
                .orElseThrow(() -> new ReservationNotFoundException(id));

        List<ReservationItem> items = reservationItemRepository.findByReservationId(id);
        Reservation cancelled = stateMachine.cancel(reservation);
        
        if (reservation == cancelled) {
            // Idempotent: already cancelled. Return current state.
            return toResponse(new ReservationAggregate(reservation, items));
        }
        
        releaseAllStock(items);
        Reservation saved = reservationRepository.save(cancelled);

        return publishAndReturn(
                new ReservationCancelledEvent(saved.getId(), saved.getOrderId()),
                new ReservationAggregate(saved, items));
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    /** Reserves stock for every line item sequentially. */
    private void reserveAllStock(ReservationAggregate aggregate) {
        for (ReservationItem item : aggregate.items()) {
            inventoryService.reserveStock(item.getSku(), item.getQuantity());
        }
    }

    /** Releases stock for every line item sequentially on cancellation. */
    private void releaseAllStock(List<ReservationItem> items) {
        for (ReservationItem item : items) {
            inventoryService.releaseStock(item.getSku(), item.getQuantity());
        }
    }

    /** Persists header + items, returns aggregate with persisted IDs. */
    private ReservationAggregate persistAggregate(ReservationAggregate aggregate) {
        Reservation savedReservation = reservationRepository.save(aggregate.reservation());

        List<ReservationItem> savedItems = aggregate.items().stream()
                .map(item -> item.toBuilder()
                        .reservationId(savedReservation.getId())
                        .build())
                .map(reservationItemRepository::save)
                .toList();

        return new ReservationAggregate(savedReservation, savedItems);
    }

    /** Publishes event via Outbox Pattern (saved in the same DB transaction) and maps to response. */
    private <E extends com.warehouse.inventory.domain.event.DomainEvent>
    ReservationResponse publishAndReturn(E domainEvent, ReservationAggregate aggregate) {
        try {
            String payload = objectMapper.writeValueAsString(domainEvent);
            OutboxEvent outboxEvent = OutboxEvent.builder()
                    .id(UUID.randomUUID())
                    .aggregateType("RESERVATION")
                    .aggregateId(aggregate.reservation().getId().toString())
                    .type(domainEvent.getEventType())
                    .payload(payload)
                    .createdAt(Instant.now())
                    .isNew(true)
                    .build();
            outboxEventRepository.save(outboxEvent);
        } catch (Exception err) {
            log.error("Failed to save outbox event {}: {}", domainEvent.getEventType(), err.getMessage());
        }
        return toResponse(aggregate);
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
