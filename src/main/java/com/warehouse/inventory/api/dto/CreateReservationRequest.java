package com.warehouse.inventory.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record CreateReservationRequest(
        @NotBlank(message = "orderId must not be blank")
        String orderId,

        @NotEmpty(message = "items must not be empty")
        @Valid
        List<ReservationItemRequest> items
) {}
