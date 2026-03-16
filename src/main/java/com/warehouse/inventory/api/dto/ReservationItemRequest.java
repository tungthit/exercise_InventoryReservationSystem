package com.warehouse.inventory.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record ReservationItemRequest(
        @NotBlank(message = "sku must not be blank")
        String sku,

        @Positive(message = "quantity must be positive")
        int quantity
) {}
