package com.warehouse.inventory.domain.state;

import com.warehouse.inventory.domain.exception.InvalidStateTransitionException;
import com.warehouse.inventory.domain.model.Reservation;
import com.warehouse.inventory.domain.model.ReservationStatus;

/** CONFIRMED is terminal – no further transitions allowed. */
public class ConfirmedState implements ReservationState {

    @Override
    public Reservation confirm(Reservation reservation) {
        // Idempotent: already confirmed, just return it
        return reservation;
    }

    @Override
    public Reservation cancel(Reservation reservation) {
        throw new InvalidStateTransitionException(ReservationStatus.CONFIRMED, "cancel");
    }
}
