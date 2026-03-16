package com.warehouse.inventory.domain.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Table("products")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Product {

    @Id
    @Column("id")
    private UUID id;

    @Column("sku")
    private String sku;

    @Column("name")
    private String name;

    @Column("created_at")
    private Instant createdAt;
}
