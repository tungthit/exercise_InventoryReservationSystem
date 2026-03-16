package com.warehouse.inventory.application.service;

import com.warehouse.inventory.domain.exception.InsufficientStockException;
import com.warehouse.inventory.domain.model.Inventory;
import com.warehouse.inventory.domain.port.LockService;
import com.warehouse.inventory.domain.repository.InventoryRepository;
import com.warehouse.inventory.infrastructure.cache.RedisInventoryCacheService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("InventoryService")
class InventoryServiceTest {

    @Mock private InventoryRepository        inventoryRepository;
    @Mock private LockService                lockService;
    @Mock private RedisInventoryCacheService cacheService;

    @InjectMocks
    private InventoryService inventoryService;

    private Inventory inventoryOf(int total, int reserved) {
        return Inventory.builder()
                .id(UUID.randomUUID())
                .productId(UUID.randomUUID())
                .totalStock(total)
                .reservedStock(reserved)
                .version(0L)
                .build();
    }

    /** Makes lockService.executeWithLock simply run the operation inline. */
    @SuppressWarnings("unchecked")
    private void passThrough() {
        when(lockService.executeWithLock(anyString(), any(Duration.class), any()))
                .thenAnswer(inv -> {
                    Supplier<Mono<?>> op = inv.getArgument(2);
                    return op.get();
                });
    }

    @Test
    @DisplayName("reserveStock succeeds when there is enough stock")
    void reserveStock_success() {
        passThrough();
        Inventory inv = inventoryOf(100, 0);
        Inventory updated = inv.reserve(30);

        when(inventoryRepository.findByProductSkuForUpdate("A100")).thenReturn(Mono.just(inv));
        when(inventoryRepository.save(any())).thenReturn(Mono.just(updated));
        when(cacheService.evict("A100")).thenReturn(Mono.empty());

        StepVerifier.create(inventoryService.reserveStock("A100", 30))
                .verifyComplete();

        verify(inventoryRepository).save(argThat(i -> i.getReservedStock() == 30));
        verify(cacheService).evict("A100");
    }

    @Test
    @DisplayName("reserveStock fails with InsufficientStockException when over-reserving")
    void reserveStock_insufficient() {
        passThrough();
        Inventory inv = inventoryOf(100, 80);  // only 20 available

        when(inventoryRepository.findByProductSkuForUpdate("A100")).thenReturn(Mono.just(inv));

        StepVerifier.create(inventoryService.reserveStock("A100", 50))
                .expectError(InsufficientStockException.class)
                .verify();

        verify(inventoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("releaseStock decreases reservedStock")
    void releaseStock_success() {
        passThrough();
        Inventory inv = inventoryOf(100, 50);
        Inventory updated = inv.release(20);

        when(inventoryRepository.findByProductSkuForUpdate("A100")).thenReturn(Mono.just(inv));
        when(inventoryRepository.save(any())).thenReturn(Mono.just(updated));
        when(cacheService.evict("A100")).thenReturn(Mono.empty());

        StepVerifier.create(inventoryService.releaseStock("A100", 20))
                .verifyComplete();

        verify(inventoryRepository).save(argThat(i -> i.getReservedStock() == 30));
    }

    @Test
    @DisplayName("getAvailableStock returns cached value when cache hits")
    void getAvailableStock_cacheHit() {
        when(cacheService.getAvailableStock("A100")).thenReturn(Mono.just(70));

        StepVerifier.create(inventoryService.getAvailableStock("A100"))
                .assertNext(stock -> assertThat(stock).isEqualTo(70))
                .verifyComplete();

        verifyNoInteractions(inventoryRepository);
    }

    @Test
    @DisplayName("getAvailableStock falls through to DB on cache miss")
    void getAvailableStock_cacheMiss() {
        Inventory inv = inventoryOf(100, 30);
        when(cacheService.getAvailableStock("A100")).thenReturn(Mono.empty());
        when(inventoryRepository.findByProductSku("A100")).thenReturn(Mono.just(inv));
        when(cacheService.setAvailableStock(eq("A100"), eq(70))).thenReturn(Mono.empty());

        StepVerifier.create(inventoryService.getAvailableStock("A100"))
                .assertNext(stock -> assertThat(stock).isEqualTo(70))
                .verifyComplete();
    }

    @Test
    @DisplayName("getAvailableStock returns empty when SKU does not exist")
    void getAvailableStock_notFound() {
        when(cacheService.getAvailableStock("UNKNOWN")).thenReturn(Mono.empty());
        when(inventoryRepository.findByProductSku("UNKNOWN")).thenReturn(Mono.empty());

        StepVerifier.create(inventoryService.getAvailableStock("UNKNOWN"))
                .verifyComplete(); // returns Mono.empty()
    }

    @Test
    @DisplayName("reserveStock fails fast when distributed lock cannot be acquired")
    void reserveStock_lockFailure() {
        when(lockService.executeWithLock(eq("inventory:lock:A100"), any(), any()))
                .thenReturn(Mono.error(new com.warehouse.inventory.infrastructure.lock.LockAcquisitionException("Lock failed")));

        StepVerifier.create(inventoryService.reserveStock("A100", 10))
                .expectError(com.warehouse.inventory.infrastructure.lock.LockAcquisitionException.class)
                .verify();

        verifyNoInteractions(inventoryRepository);
        verifyNoInteractions(cacheService);
    }

    /**
     * Concurrency guard – verifies the domain model prevents overselling
     * even when multiple simultaneous calls arrive (lock pass-through).
     *
     * NOTE: In a real integration test (Testcontainers), the DB-level
     * FOR UPDATE lock would be the ultimate guard.  This unit test validates
     * the domain invariant layer.
     */
    @Test
    @DisplayName("domain model rejects oversell regardless of concurrency")
    void domainOversellIsImpossible() {
        AtomicInteger reservedSoFar = new AtomicInteger(0);
        int total = 100;

        // Simulate 10 concurrent attempts each wanting 15 units (total = 150)
        long successful = java.util.stream.IntStream.range(0, 10)
                .parallel()
                .filter(i -> {
                    int current = reservedSoFar.get();
                    Inventory inv = inventoryOf(total, current);
                    try {
                        inv.reserve(15);
                        // Only update if CAS succeeds (simulate optimistic check)
                        return reservedSoFar.compareAndSet(current, current + 15);
                    } catch (InsufficientStockException e) {
                        return false;
                    }
                })
                .count();

        assertThat(reservedSoFar.get()).isLessThanOrEqualTo(total);
        assertThat(successful).isLessThanOrEqualTo(6);  // 6 * 15 = 90 ≤ 100
    }
}
