package com.warehouse.inventory.domain.port;

import com.warehouse.inventory.domain.event.DomainEvent;


/**
 * Output port – infrastructure-agnostic contract for publishing domain events.
 * The NATS implementation lives in the infrastructure layer.
 */
public interface EventPublisher {
    <T extends DomainEvent> void publish(T event);
}
