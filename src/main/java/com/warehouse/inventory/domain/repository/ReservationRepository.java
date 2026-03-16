package com.warehouse.inventory.domain.repository;

import com.warehouse.inventory.domain.model.Reservation;
import org.springframework.data.repository.CrudRepository;

import java.util.UUID;

public interface ReservationRepository extends CrudRepository<Reservation, UUID> {
}
