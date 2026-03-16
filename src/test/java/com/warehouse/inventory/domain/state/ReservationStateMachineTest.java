package com.warehouse.inventory.domain.state;

import com.warehouse.inventory.domain.exception.InvalidStateTransitionException;
import com.warehouse.inventory.domain.model.Reservation;
import com.warehouse.inventory.domain.model.ReservationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@DisplayName("ReservationStateMachine")
class ReservationStateMachineTest {

    private ReservationStateMachine stateMachine;

    @BeforeEach
    void setUp() {
        stateMachine = new ReservationStateMachine();
    }

    private Reservation reservationWith(ReservationStatus status) {
        return Reservation.builder()
                .id(UUID.randomUUID())
                .orderId("ORD-TEST")
                .status(status)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    // ── Happy paths ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("PENDING -> CONFIRMED via confirm()")
    void pendingToConfirmed() {
        Reservation pending = reservationWith(ReservationStatus.PENDING);
        Reservation result = stateMachine.confirm(pending);
        assertThat(result.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
    }

    @Test
    @DisplayName("PENDING -> CANCELLED via cancel()")
    void pendingToCancelled() {
        Reservation pending = reservationWith(ReservationStatus.PENDING);
        Reservation result = stateMachine.cancel(pending);
        assertThat(result.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
    }

    // ── Terminal state guards ─────────────────────────────────────────────────

    @Test
    @DisplayName("CONFIRMED is idempotent – confirm() returns same instance")
    void confirmedCannotConfirmAgain() {
        Reservation confirmed = reservationWith(ReservationStatus.CONFIRMED);
        Reservation result = stateMachine.confirm(confirmed);
        assertThat(result).isSameAs(confirmed);
        assertThat(result.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
    }

    @Test
    @DisplayName("CONFIRMED is terminal – cancel() throws")
    void confirmedCannotBeCancelled() {
        Reservation confirmed = reservationWith(ReservationStatus.CONFIRMED);
        assertThatThrownBy(() -> stateMachine.cancel(confirmed))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    @DisplayName("CANCELLED is terminal – confirm() throws")
    void cancelledCannotBeConfirmed() {
        Reservation cancelled = reservationWith(ReservationStatus.CANCELLED);
        assertThatThrownBy(() -> stateMachine.confirm(cancelled))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    @DisplayName("CANCELLED is idempotent – cancel() returns same instance")
    void cancelledCannotBeCancelledAgain() {
        Reservation cancelled = reservationWith(ReservationStatus.CANCELLED);
        Reservation result = stateMachine.cancel(cancelled);
        assertThat(result).isSameAs(cancelled);
        assertThat(result.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
    }

    // ── Immutability ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("confirm() returns new instance, original unchanged")
    void confirmDoesNotMutateOriginal() {
        Reservation pending = reservationWith(ReservationStatus.PENDING);
        Reservation confirmed = stateMachine.confirm(pending);
        assertThat(pending.getStatus()).isEqualTo(ReservationStatus.PENDING);
        assertThat(confirmed).isNotSameAs(pending);
    }
}
