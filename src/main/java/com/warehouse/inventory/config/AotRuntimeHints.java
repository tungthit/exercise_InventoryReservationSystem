package com.warehouse.inventory.config;

import com.warehouse.inventory.api.dto.CreateReservationRequest;
import com.warehouse.inventory.api.dto.ReservationItemRequest;
import com.warehouse.inventory.api.dto.ReservationItemResponse;
import com.warehouse.inventory.api.dto.ReservationResponse;
import com.warehouse.inventory.domain.event.ReservationCancelledEvent;
import com.warehouse.inventory.domain.event.ReservationConfirmedEvent;
import com.warehouse.inventory.domain.event.ReservationCreatedEvent;
import com.warehouse.inventory.domain.model.Inventory;
import com.warehouse.inventory.domain.model.Product;
import com.warehouse.inventory.domain.model.Reservation;
import com.warehouse.inventory.domain.model.ReservationItem;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;

/**
 * AOT Runtime Hints – ensure all types needed for reflection/serialization
 * are registered when compiling to a GraalVM native image.
 */
public class AotRuntimeHints implements RuntimeHintsRegistrar {

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        // Domain models
        hints.reflection().registerType(Product.class);
        hints.reflection().registerType(Inventory.class);
        hints.reflection().registerType(Reservation.class);
        hints.reflection().registerType(ReservationItem.class);

        // DTOs
        hints.reflection().registerType(CreateReservationRequest.class);
        hints.reflection().registerType(ReservationItemRequest.class);
        hints.reflection().registerType(ReservationResponse.class);
        hints.reflection().registerType(ReservationItemResponse.class);

        // Events
        hints.reflection().registerType(ReservationCreatedEvent.class);
        hints.reflection().registerType(ReservationConfirmedEvent.class);
        hints.reflection().registerType(ReservationCancelledEvent.class);

        // Flyway SQL scripts
        hints.resources().registerPattern("db/migration/*.sql");
    }
}
