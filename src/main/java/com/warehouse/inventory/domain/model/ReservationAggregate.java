package com.warehouse.inventory.domain.model;

import java.util.List;

/**
 * Transient aggregate root combining a Reservation header and its line items.
 * R2DBC has no cascading; this record allows the service layer to treat them
 * as a single logical unit without adding ORM-level coupling.
 */
public record ReservationAggregate(Reservation reservation, List<ReservationItem> items) {
}
