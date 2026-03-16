package com.warehouse.inventory.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.warehouse.inventory.domain.event.DomainEvent;
import com.warehouse.inventory.domain.port.EventPublisher;
import io.nats.client.JetStream;
import io.nats.client.Message;
import io.nats.client.impl.NatsMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;


/**
 * NATS JetStream implementation of {@link EventPublisher}.
 *
 * <p>Publishes events to subjects of the form {@code reservations.<event_type_lower>},
 * e.g. {@code reservations.reservation_created}.
 *
 * <p>Because {@code JetStream.publish()} is a blocking call, it is wrapped with
 * {@code Schedulers.boundedElastic()} to avoid blocking the Netty event loop.
 * This is the standard bridge pattern for blocking I/O inside reactive pipelines.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NatsEventPublisher implements EventPublisher {

    private final JetStream    jetStream;
    private final ObjectMapper objectMapper;

    @Override
    public <T extends DomainEvent> void publish(T event) {
        try {
            String subject = "reservations." + event.getEventType().toLowerCase();
            byte[] data    = objectMapper.writeValueAsBytes(event);

            Message message = NatsMessage.builder()
                    .subject(subject)
                    .data(data)
                    .build();

            jetStream.publish(message);    // blocks briefly for PubAck
            log.info("Published event '{}' to NATS subject '{}'", event.getEventType(), subject);
        } catch (Exception e) {
            log.error("Failed to publish event '{}' to NATS", event.getEventType(), e);
            throw new RuntimeException("Failed to publish event to NATS", e);
        }
    }
}
