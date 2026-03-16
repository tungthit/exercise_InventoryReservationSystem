package com.warehouse.inventory.api.controller;

import com.warehouse.inventory.api.dto.CreateReservationRequest;
import com.warehouse.inventory.api.dto.ReservationResponse;
import com.warehouse.inventory.application.service.ReservationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Reservation REST API.
 *
 * <p>All methods return {@link Mono} so Netty never blocks a thread
 * waiting for DB or messaging I/O.
 */
@Slf4j
@RestController
@RequestMapping("/reservations")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;

    /**
     * Create a new inventory reservation.
     *
     * <pre>POST /api/reservations</pre>
     * Body: {@code {"orderId":"ORD-1001","items":[{"sku":"A100","quantity":5}]}}
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<ReservationResponse> createReservation(
            @Valid @RequestBody CreateReservationRequest request) {
        log.info("Creating reservation for orderId={}", request.orderId());
        return reservationService.createReservation(request);
    }

    /**
     * Confirm a PENDING reservation.
     *
     * <pre>POST /api/reservations/{id}/confirm</pre>
     */
    @PostMapping("/{id}/confirm")
    public Mono<ReservationResponse> confirmReservation(@PathVariable UUID id) {
        log.info("Confirming reservation id={}", id);
        return reservationService.confirmReservation(id);
    }

    /**
     * Cancel a PENDING reservation and release reserved stock.
     *
     * <pre>POST /api/reservations/{id}/cancel</pre>
     */
    @PostMapping("/{id}/cancel")
    public Mono<ReservationResponse> cancelReservation(@PathVariable UUID id) {
        log.info("Cancelling reservation id={}", id);
        return reservationService.cancelReservation(id);
    }
}
