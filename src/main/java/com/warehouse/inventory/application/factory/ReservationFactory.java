package com.warehouse.inventory.application.factory;

import com.warehouse.inventory.api.dto.CreateReservationRequest;
import com.warehouse.inventory.domain.exception.ProductNotFoundException;
import com.warehouse.inventory.domain.model.*;
import com.warehouse.inventory.domain.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

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

    public Mono<ReservationAggregate> create(CreateReservationRequest request) {
        List<String> requestedSkus = request.items().stream()
                .map(item -> item.sku())
                .distinct()
                .toList();

        return productRepository.findBySkuIn(requestedSkus)
                .collectList()
                .flatMap(found -> {
                    Set<String> foundSkus = found.stream()
                            .map(Product::getSku)
                            .collect(Collectors.toSet());

                    List<String> missing = requestedSkus.stream()
                            .filter(sku -> !foundSkus.contains(sku))
                            .toList();

                    if (!missing.isEmpty()) {
                        return Mono.error(new ProductNotFoundException(
                                "Unknown SKU(s): " + String.join(", ", missing)));
                    }

                    UUID reservationId = UUID.randomUUID();
                    Instant now = Instant.now();

                    Reservation reservation = Reservation.builder()
                            .id(reservationId)
                            .orderId(request.orderId())
                            .status(ReservationStatus.PENDING)
                            .createdAt(now)
                            .updatedAt(now)
                            .isNew(true)   // signals R2DBC to INSERT, not UPDATE
                            .build();

                    List<ReservationItem> items = request.items().stream()
                            .map(req -> ReservationItem.builder()
                                    .id(UUID.randomUUID())
                                    .reservationId(reservationId)
                                    .sku(req.sku())
                                    .quantity(req.quantity())
                                    .isNew(true)   // signals R2DBC to INSERT, not UPDATE
                                    .build())
                            .toList();

                    return Mono.just(new ReservationAggregate(reservation, items));
                });
    }
}
