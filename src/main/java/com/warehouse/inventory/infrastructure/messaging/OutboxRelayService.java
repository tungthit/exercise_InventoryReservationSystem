package com.warehouse.inventory.infrastructure.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.warehouse.inventory.domain.model.OutboxEvent;
import com.warehouse.inventory.domain.repository.OutboxEventRepository;
import io.nats.client.JetStream;
import io.nats.client.Message;
import io.nats.client.impl.NatsMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import reactor.core.publisher.Mono;

import java.time.Instant;

/**
 * Polls the outbox_events table for unpublished messages and sends them to NATS.
 * 
 * Uses FOR UPDATE SKIP LOCKED in the repository to safely allow multiple 
 * instances (horizontal scaling) to pull events concurrently without deadlocks.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRelayService {

    private final OutboxEventRepository outboxEventRepository;
    private final JetStream             jetStream;
    private final org.springframework.transaction.reactive.TransactionalOperator transactionalOperator;

    @Scheduled(fixedDelay = 2000)
    public void relayEvents() {
        // Since @Scheduled is imperative and we use Reactive R2DBC, 
        // we subscribe to trigger the flow and wrap it in a proper reactive
        // transaction operator instead of a @Transactional annotation on a void method.
        outboxEventRepository.findUnprocessedAndLock()
                .flatMap(this::publishAndMarkProcessed)
                .as(transactionalOperator::transactional)
                .subscribe(
                        success -> {}, 
                        error -> log.error("Error during outbox relay", error)
                );
    }

    private Mono<Void> publishAndMarkProcessed(OutboxEvent outboxEvent) {
        return Mono.fromCallable(() -> {
            // NATS subject partitioning: reservations.<reservationId>.<eventType>
            // This guarantees strict chronological ordering for a specific reservation.
            String subject = String.format("reservations.%s.%s", 
                    outboxEvent.getAggregateId(), 
                    outboxEvent.getType().toLowerCase());

            Message message = NatsMessage.builder()
                    .subject(subject)
                    .data(outboxEvent.getPayload().getBytes())
                    .build();

            jetStream.publish(message); // Wait for ACK from NATS
            return true;
        })
        // If NATS publish succeeds, mark as processed in DB
        .flatMap(success -> outboxEventRepository.markAsProcessed(outboxEvent.getId(), Instant.now()))
        .then()
        .onErrorResume(e -> {
            log.error("Failed to relay outbox event id: {}", outboxEvent.getId(), e);
            // Ignore error for this specific event and continue processing others
            return Mono.empty(); 
        });
    }
}
