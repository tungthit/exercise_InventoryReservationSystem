package com.warehouse.inventory.domain.state;

import com.warehouse.inventory.domain.model.Reservation;
import com.warehouse.inventory.domain.model.ReservationStatus;

import java.time.Instant;

/**
 * PENDING → CONFIRMED allowed.
 * PENDING → CANCELLED allowed.
 */
public class PendingState implements ReservationState {

    @Override
    public Reservation confirm(Reservation reservation) {
        return reservation.toBuilder()
                .status(ReservationStatus.CONFIRMED)
                .updatedAt(Instant.now())
                .build();
    }

    @Override
    public Reservation cancel(Reservation reservation) {
        return reservation.toBuilder()
                .status(ReservationStatus.CANCELLED)
                .updatedAt(Instant.now())
                .build();
    }
}
