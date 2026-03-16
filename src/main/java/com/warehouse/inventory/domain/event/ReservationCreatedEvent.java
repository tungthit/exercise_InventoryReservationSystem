package com.warehouse.inventory.domain.event;

import java.time.Instant;
import java.util.UUID;

public record ReservationCreatedEvent(
        UUID reservationId,
        String orderId,
        Instant occurredAt
) implements DomainEvent {

    public ReservationCreatedEvent(UUID reservationId, String orderId) {
        this(reservationId, orderId, Instant.now());
    }

    @Override public String getEventType()       { return "RESERVATION_CREATED"; }
    @Override public UUID   getReservationId()   { return reservationId; }
    @Override public Instant getOccurredAt()     { return occurredAt; }
}
