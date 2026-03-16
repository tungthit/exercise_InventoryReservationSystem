package com.warehouse.inventory.domain.repository;

import com.warehouse.inventory.domain.model.ReservationItem;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

import java.util.UUID;

public interface ReservationItemRepository extends ReactiveCrudRepository<ReservationItem, UUID> {

    Flux<ReservationItem> findByReservationId(UUID reservationId);
}
