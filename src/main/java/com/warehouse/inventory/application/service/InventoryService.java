package com.warehouse.inventory.application.service;

import com.warehouse.inventory.domain.exception.InsufficientStockException;
import com.warehouse.inventory.domain.port.LockService;
import com.warehouse.inventory.domain.repository.InventoryRepository;
import com.warehouse.inventory.infrastructure.cache.RedisInventoryCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Handles all inventory stock mutations.
 *
 * <p><strong>Concurrency strategy (two-level):</strong>
 * <ol>
 *   <li><em>Redisson distributed lock</em> – per-SKU lock acquired before the
 *       DB query. Prevents concurrent threads/pods from reading the same
 *       un-committed stock value simultaneously.</li>
 *   <li><em>Pessimistic DB row lock</em> ({@code SELECT … FOR UPDATE}) – inside
 *       the R2DBC transaction as a second guard; prevents any concurrent DB
 *       transaction from reading the row mid-flight.</li>
 * </ol>
 * Together these make overselling impossible both within a single replica and
 * across a horizontally scaled cluster.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryService {

    private static final Duration LOCK_LEASE = Duration.ofSeconds(10);
    private static final String  LOCK_PREFIX = "inventory:lock:";

    private final InventoryRepository      inventoryRepository;
    private final LockService              lockService;
    private final RedisInventoryCacheService cacheService;

    /**
     * Reserves {@code quantity} units of {@code sku}.
     * Must be called outside a transaction; it starts one internally.
     */
    @Transactional
    public Mono<Void> reserveStock(String sku, int quantity) {
        return lockService.executeWithLock(
                LOCK_PREFIX + sku, LOCK_LEASE,
                () -> inventoryRepository.findByProductSkuForUpdate(sku)
                        .switchIfEmpty(Mono.error(
                                new InsufficientStockException("No inventory record found for SKU: " + sku)))
                        .flatMap(inventory -> {
                            log.debug("Reserving {} units of SKU '{}' (available: {})",
                                    quantity, sku, inventory.getAvailableStock());
                            return inventoryRepository.save(inventory.reserve(quantity));
                        })
                        .doOnSuccess(i -> log.info("Reserved {} units of SKU '{}'", quantity, sku))
                        .flatMap(saved -> cacheService.evict(sku))
        );
    }

    /**
     * Releases {@code quantity} units of {@code sku} back to available stock.
     */
    @Transactional
    public Mono<Void> releaseStock(String sku, int quantity) {
        return lockService.executeWithLock(
                LOCK_PREFIX + sku, LOCK_LEASE,
                () -> inventoryRepository.findByProductSkuForUpdate(sku)
                        .flatMap(inventory -> {
                            log.debug("Releasing {} units of SKU '{}'", quantity, sku);
                            return inventoryRepository.save(inventory.release(quantity));
                        })
                        .doOnSuccess(i -> log.info("Released {} units of SKU '{}'", quantity, sku))
                        .flatMap(saved -> cacheService.evict(sku))
        );
    }

    /** Returns available stock for a SKU, checking the Redis cache first. */
    public Mono<Integer> getAvailableStock(String sku) {
        return cacheService.getAvailableStock(sku)
                .switchIfEmpty(
                        inventoryRepository.findByProductSku(sku)
                                .map(inv -> {
                                    cacheService.setAvailableStock(sku, inv.getAvailableStock()).subscribe();
                                    return inv.getAvailableStock();
                                })
                );
    }
}
