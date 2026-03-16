package com.warehouse.inventory.domain.repository;

import com.warehouse.inventory.domain.model.OutboxEvent;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

public interface OutboxEventRepository extends ReactiveCrudRepository<OutboxEvent, UUID> {

    @Query("SELECT * FROM outbox_events WHERE processed_at IS NULL ORDER BY created_at ASC LIMIT 100 FOR UPDATE SKIP LOCKED")
    Flux<OutboxEvent> findUnprocessedAndLock();

    @Modifying
    @Query("UPDATE outbox_events SET processed_at = :now WHERE id = :id")
    Mono<Integer> markAsProcessed(UUID id, Instant now);
}
