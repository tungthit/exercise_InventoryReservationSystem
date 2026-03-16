package com.warehouse.inventory.domain.state;

import com.warehouse.inventory.domain.exception.InvalidStateTransitionException;
import com.warehouse.inventory.domain.model.Reservation;
import com.warehouse.inventory.domain.model.ReservationStatus;

/** CANCELLED is terminal – no further transitions allowed. */
public class CancelledState implements ReservationState {

    @Override
    public Reservation confirm(Reservation reservation) {
        throw new InvalidStateTransitionException(ReservationStatus.CANCELLED, "confirm");
    }

    @Override
    public Reservation cancel(Reservation reservation) {
        throw new InvalidStateTransitionException(ReservationStatus.CANCELLED, "cancel");
    }
}
