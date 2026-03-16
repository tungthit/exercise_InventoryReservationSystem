package com.warehouse.inventory.domain.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.util.UUID;

@Table("reservation_items")
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class ReservationItem {

    @Id
    @Column("id")
    private UUID id;

    @Column("reservation_id")
    private UUID reservationId;

    @Column("sku")
    private String sku;

    @Column("quantity")
    private int quantity;
}
