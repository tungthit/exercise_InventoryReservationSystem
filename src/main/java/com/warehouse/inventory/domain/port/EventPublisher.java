package com.warehouse.inventory.domain.port;

import com.warehouse.inventory.domain.event.DomainEvent;
import reactor.core.publisher.Mono;

/**
 * Output port – infrastructure-agnostic contract for publishing domain events.
 * The NATS implementation lives in the infrastructure layer.
 */
public interface EventPublisher {
    <T extends DomainEvent> Mono<Void> publish(T event);
}
