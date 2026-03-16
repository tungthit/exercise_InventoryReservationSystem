package com.warehouse.inventory.domain.exception;

import com.warehouse.inventory.domain.model.ReservationStatus;

public class InvalidStateTransitionException extends RuntimeException {
    public InvalidStateTransitionException(ReservationStatus from, String action) {
        super("Cannot perform '" + action + "' on a reservation in state: " + from);
    }
}
