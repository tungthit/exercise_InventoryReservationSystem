package com.warehouse.inventory.domain.repository;

import com.warehouse.inventory.domain.model.ReservationItem;
import org.springframework.data.repository.CrudRepository;

import java.util.List;
import java.util.UUID;

public interface ReservationItemRepository extends CrudRepository<ReservationItem, UUID> {

    List<ReservationItem> findByReservationId(UUID reservationId);
}
