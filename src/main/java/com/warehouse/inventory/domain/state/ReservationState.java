package com.warehouse.inventory.domain.state;

import com.warehouse.inventory.domain.model.Reservation;

/**
 * State Pattern – defines all valid actions on a Reservation.
 * Each concrete state either performs the transition or throws
 * {@link com.warehouse.inventory.domain.exception.InvalidStateTransitionException}.
 */
public interface ReservationState {

    /** Attempt to confirm this reservation. */
    Reservation confirm(Reservation reservation);

    /** Attempt to cancel this reservation. */
    Reservation cancel(Reservation reservation);
}
