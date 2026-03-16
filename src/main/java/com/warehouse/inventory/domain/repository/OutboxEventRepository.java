package com.warehouse.inventory.domain.repository;

import com.warehouse.inventory.domain.model.OutboxEvent;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends CrudRepository<OutboxEvent, UUID> {

    @Query("SELECT * FROM outbox_events WHERE processed_at IS NULL ORDER BY created_at ASC LIMIT 100 FOR UPDATE SKIP LOCKED")
    List<OutboxEvent> findUnprocessedAndLock();

    @Modifying
    @Query("UPDATE outbox_events SET processed_at = :now WHERE id = :id")
    int markAsProcessed(UUID id, Instant now);
}
