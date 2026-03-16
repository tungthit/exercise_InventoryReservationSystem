package com.warehouse.inventory.domain.event;

import java.time.Instant;
import java.util.UUID;

/** Base type for all domain events published to NATS. */
public interface DomainEvent {
    UUID getReservationId();
    String getEventType();
    Instant getOccurredAt();
}
