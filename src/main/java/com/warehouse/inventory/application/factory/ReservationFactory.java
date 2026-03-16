package com.warehouse.inventory.application.factory;

import com.warehouse.inventory.api.dto.CreateReservationRequest;
import com.warehouse.inventory.domain.exception.ProductNotFoundException;
import com.warehouse.inventory.domain.model.*;
import com.warehouse.inventory.domain.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Factory Pattern – encapsulates all creation logic for a {@link ReservationAggregate}.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Validates that every requested SKU exists in the product catalogue.</li>
 *   <li>Constructs the {@link Reservation} header with PENDING status.</li>
 *   <li>Constructs each {@link ReservationItem} line.</li>
 * </ul>
 *
 * <p>The factory does NOT write to the database; saving is the service's job.
 */
@Component
@RequiredArgsConstructor
public class ReservationFactory {

    private final ProductRepository productRepository;

    public ReservationAggregate create(CreateReservationRequest request) {
        List<String> requestedSkus = request.items().stream()
                .map(item -> item.sku())
                .distinct()
                .toList();

        List<Product> found = productRepository.findBySkuIn(requestedSkus);
        
        Set<String> foundSkus = found.stream()
                .map(Product::getSku)
                .collect(Collectors.toSet());

        List<String> missing = requestedSkus.stream()
                .filter(sku -> !foundSkus.contains(sku))
                .toList();

        if (!missing.isEmpty()) {
            throw new ProductNotFoundException(
                    "Unknown SKU(s): " + String.join(", ", missing));
        }

        UUID reservationId = UUID.randomUUID();
        Instant now = Instant.now();

        Reservation reservation = Reservation.builder()
                .id(reservationId)
                .orderId(request.orderId())
                .status(ReservationStatus.PENDING)
                .createdAt(now)
                .updatedAt(now)
                .isNew(true)   // signals Spring JDBC to INSERT, not UPDATE
                .build();

        List<ReservationItem> items = request.items().stream()
                .map(req -> ReservationItem.builder()
                        .id(UUID.randomUUID())
                        .reservationId(reservationId)
                        .sku(req.sku())
                        .quantity(req.quantity())
                        .isNew(true)   // signals Spring JDBC to INSERT, not UPDATE
                        .build())
                .toList();

        return new ReservationAggregate(reservation, items);
    }
}
