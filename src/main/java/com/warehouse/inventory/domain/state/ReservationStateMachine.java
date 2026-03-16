package com.warehouse.inventory.domain.state;

import com.warehouse.inventory.domain.model.Reservation;
import com.warehouse.inventory.domain.model.ReservationStatus;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * State Pattern orchestrator.
 *
 * Maintains an immutable map from {@link ReservationStatus} to its
 * corresponding {@link ReservationState} implementation and delegates
 * lifecycle operations to the current state.
 *
 * Adding a new state = add an entry to the map; nothing else changes.
 */
@Component
public class ReservationStateMachine {

    private final Map<ReservationStatus, ReservationState> states = Map.of(
            ReservationStatus.PENDING,   new PendingState(),
            ReservationStatus.CONFIRMED, new ConfirmedState(),
            ReservationStatus.CANCELLED, new CancelledState()
    );

    /**
     * Transitions the reservation to CONFIRMED.
     *
     * @throws com.warehouse.inventory.domain.exception.InvalidStateTransitionException
     *         if the current state does not allow confirmation
     */
    public Reservation confirm(Reservation reservation) {
        return resolve(reservation.getStatus()).confirm(reservation);
    }

    /**
     * Transitions the reservation to CANCELLED.
     *
     * @throws com.warehouse.inventory.domain.exception.InvalidStateTransitionException
     *         if the current state does not allow cancellation
     */
    public Reservation cancel(Reservation reservation) {
        return resolve(reservation.getStatus()).cancel(reservation);
    }

    private ReservationState resolve(ReservationStatus status) {
        ReservationState state = states.get(status);
        if (state == null) {
            throw new IllegalStateException("Unknown reservation status: " + status);
        }
        return state;
    }
}
