package com.warehouse.inventory.application.service;

import com.warehouse.inventory.domain.exception.InsufficientStockException;
import com.warehouse.inventory.domain.port.LockService;
import com.warehouse.inventory.domain.repository.InventoryRepository;
import com.warehouse.inventory.infrastructure.cache.RedisInventoryCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    public void reserveStock(String sku, int quantity) {
        lockService.executeWithLock(
                LOCK_PREFIX + sku, LOCK_LEASE,
                () -> {
                    com.warehouse.inventory.domain.model.Inventory inventory = inventoryRepository.findByProductSkuForUpdate(sku)
                            .orElseThrow(() -> new InsufficientStockException("No inventory record found for SKU: " + sku));

                    log.debug("Reserving {} units of SKU '{}' (available: {})", quantity, sku, inventory.getAvailableStock());
                    
                    inventoryRepository.save(inventory.reserve(quantity));
                    log.info("Reserved {} units of SKU '{}'", quantity, sku);
                    cacheService.evict(sku);
                    
                    return null;
                }
        );
    }

    /**
     * Releases {@code quantity} units of {@code sku} back to available stock.
     */
    @Transactional
    public void releaseStock(String sku, int quantity) {
        lockService.executeWithLock(
                LOCK_PREFIX + sku, LOCK_LEASE,
                () -> {
                    inventoryRepository.findByProductSkuForUpdate(sku).ifPresent(inventory -> {
                        log.debug("Releasing {} units of SKU '{}'", quantity, sku);
                        inventoryRepository.save(inventory.release(quantity));
                        log.info("Released {} units of SKU '{}'", quantity, sku);
                        cacheService.evict(sku);
                    });
                    return null;
                }
        );
    }

    /**
     * Returns available stock for a SKU, checking the Redis cache first.
     */
    public Integer getAvailableStock(String sku) {
        Integer cached = cacheService.getAvailableStock(sku);
        if (cached != null) {
            return cached;
        }

        com.warehouse.inventory.domain.model.Inventory inv = inventoryRepository.findByProductSku(sku).orElse(null);
        if (inv != null) {
            cacheService.setAvailableStock(sku, inv.getAvailableStock());
            return inv.getAvailableStock();
        }
        return 0;
    }
}
