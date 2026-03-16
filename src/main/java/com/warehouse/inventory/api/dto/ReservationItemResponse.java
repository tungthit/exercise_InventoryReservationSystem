package com.warehouse.inventory.api.dto;

import java.time.Instant;
import java.util.UUID;

public record ReservationItemResponse(UUID id, String sku, int quantity) {}
