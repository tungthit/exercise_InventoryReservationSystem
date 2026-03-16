package com.warehouse.inventory.domain.model;

import com.warehouse.inventory.domain.exception.InsufficientStockException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Inventory domain model")
class InventoryTest {

    private Inventory inventory(int total, int reserved) {
        return Inventory.builder()
                .id(UUID.randomUUID())
                .productId(UUID.randomUUID())
                .totalStock(total)
                .reservedStock(reserved)
                .version(0L)
                .build();
    }

    @Test
    @DisplayName("availableStock = total - reserved")
    void availableStockCalculation() {
        assertThat(inventory(100, 30).getAvailableStock()).isEqualTo(70);
    }

    @Test
    @DisplayName("canReserve returns true when enough stock")
    void canReserveTrue() {
        assertThat(inventory(100, 30).canReserve(70)).isTrue();
    }

    @Test
    @DisplayName("canReserve returns false when insufficient stock")
    void canReserveFalse() {
        assertThat(inventory(100, 30).canReserve(71)).isFalse();
    }

    @Test
    @DisplayName("reserve() increases reservedStock correctly")
    void reserveIncrementsCorrectly() {
        Inventory updated = inventory(100, 30).reserve(20);
        assertThat(updated.getReservedStock()).isEqualTo(50);
        assertThat(updated.getAvailableStock()).isEqualTo(50);
    }

    @Test
    @DisplayName("reserve() throws when quantity exceeds available")
    void reserveThrowsOnOversell() {
        Inventory inv = inventory(100, 80);   // only 20 available
        assertThatThrownBy(() -> inv.reserve(21))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessageContaining("20");
    }

    @Test
    @DisplayName("reserve() does not mutate original instance")
    void reserveIsImmutable() {
        Inventory original = inventory(100, 0);
        original.reserve(50);
        assertThat(original.getReservedStock()).isZero();
    }

    @Test
    @DisplayName("release() decreases reservedStock correctly")
    void releaseDecrementsCorrectly() {
        Inventory updated = inventory(100, 50).release(20);
        assertThat(updated.getReservedStock()).isEqualTo(30);
    }

    @Test
    @DisplayName("release() does not go negative")
    void releaseDoesNotGoNegative() {
        Inventory updated = inventory(100, 10).release(999);
        assertThat(updated.getReservedStock()).isZero();
    }
}
