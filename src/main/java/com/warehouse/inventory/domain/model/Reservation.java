package com.warehouse.inventory.domain.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Reservation entity.
 *
 * <p>Implements {@link Persistable} so that Spring Data R2DBC can tell
 * whether to issue an {@code INSERT} or an {@code UPDATE}. When a new
 * aggregate is built in-memory with a client-generated UUID, R2DBC would
 * otherwise see a non-null ID and call {@code UPDATE}, failing because no
 * row exists yet. The transient {@code isNew} flag is set to {@code true}
 * by the builder and cleared after the first successful save.
 */
@Table("reservations")
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class Reservation implements Persistable<UUID> {

    @Id
    @Column("id")
    private UUID id;

    @Column("order_id")
    private String orderId;

    @Column("status")
    private ReservationStatus status;

    @Column("created_at")
    private Instant createdAt;

    @Column("updated_at")
    private Instant updatedAt;

    /**
     * Transient flag: {@code true} means this is a brand-new entity that
     * has never been persisted. Spring Data checks {@link #isNew()} before
     * deciding INSERT vs UPDATE. The flag is NOT stored in the DB column.
     */
    @Transient
    @Builder.Default
    private boolean isNew = false;

    @Override
    public boolean isNew() {
        return isNew;
    }
}
