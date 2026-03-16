package com.warehouse.inventory.api.dto;

import com.warehouse.inventory.domain.model.ReservationStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ReservationResponse(
        UUID id,
        String orderId,
        ReservationStatus status,
        List<ReservationItemResponse> items,
        Instant createdAt,
        Instant updatedAt
) {}
