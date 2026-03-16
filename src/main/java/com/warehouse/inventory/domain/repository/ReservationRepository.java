package com.warehouse.inventory.domain.repository;

import com.warehouse.inventory.domain.model.Reservation;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

import java.util.UUID;

public interface ReservationRepository extends ReactiveCrudRepository<Reservation, UUID> {
}
