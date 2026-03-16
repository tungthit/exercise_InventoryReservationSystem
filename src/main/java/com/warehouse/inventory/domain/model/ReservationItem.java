package com.warehouse.inventory.domain.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.util.UUID;

@Table("reservation_items")
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class ReservationItem implements Persistable<UUID> {

    @Id
    @Column("id")
    private UUID id;

    @Column("reservation_id")
    private UUID reservationId;

    @Column("sku")
    private String sku;

    @Column("quantity")
    private int quantity;

    @Transient
    @Builder.Default
    private boolean isNew = false;

    @Override
    public boolean isNew() {
        return isNew;
    }
}
