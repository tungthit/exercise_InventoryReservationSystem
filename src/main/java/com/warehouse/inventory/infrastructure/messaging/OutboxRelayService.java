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
    @Scheduled(fixedDelay = 2000)
    @org.springframework.transaction.annotation.Transactional
    public void relayEvents() {
        java.util.List<OutboxEvent> events = outboxEventRepository.findUnprocessedAndLock();
        
        for (OutboxEvent outboxEvent : events) {
            try {
                // NATS subject partitioning: reservations.<reservationId>.<eventType>
                String subject = String.format("reservations.%s.%s", 
                        outboxEvent.getAggregateId(), 
                        outboxEvent.getType().toLowerCase());

                Message message = NatsMessage.builder()
                        .subject(subject)
                        .data(outboxEvent.getPayload().getBytes())
                        .build();

                jetStream.publish(message); // Wait for ACK from NATS
                
                // Mark as processed in DB
                outboxEventRepository.markAsProcessed(outboxEvent.getId(), Instant.now());
            } catch (Exception e) {
                log.error("Failed to relay outbox event id: {}", outboxEvent.getId(), e);
                // Ignore error for this specific event and continue processing others
            }
        }
    }
}
