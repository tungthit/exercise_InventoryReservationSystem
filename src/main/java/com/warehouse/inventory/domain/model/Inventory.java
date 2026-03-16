package com.warehouse.inventory.domain.model;

import com.warehouse.inventory.domain.exception.InsufficientStockException;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.util.UUID;

/**
 * Represents on-hand stock for a single product.
 *
 * Invariants enforced here (domain logic):
 *   - reservedStock <= totalStock  (also enforced by DB CHECK constraint)
 *   - availableStock = totalStock - reservedStock
 *
 * {@code @Version} enables optimistic locking as a secondary defence;
 * the primary concurrency guard is a Redisson per-SKU distributed lock
 * combined with a pessimistic {@code SELECT ... FOR UPDATE} DB query.
 */
@Table("inventory")
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class Inventory {

    @Id
    @Column("id")
    private UUID id;

    @Column("product_id")
    private UUID productId;

    @Column("total_stock")
    private int totalStock;

    @Column("reserved_stock")
    private int reservedStock;

    @Version
    @Column("version")
    private Long version;

    // ── Domain behaviour ───────────────────────────────────────────────────

    public int getAvailableStock() {
        return totalStock - reservedStock;
    }

    public boolean canReserve(int quantity) {
        return getAvailableStock() >= quantity;
    }

    /**
     * Returns a NEW Inventory instance with reservedStock increased.
     * The caller must persist the result.
     */
    public Inventory reserve(int quantity) {
        if (!canReserve(quantity)) {
            throw new InsufficientStockException(
                    "Insufficient stock. Available: " + getAvailableStock()
                    + ", requested: " + quantity);
        }
        return this.toBuilder()
                .reservedStock(this.reservedStock + quantity)
                .build();
    }

    /**
     * Returns a NEW Inventory instance with reservedStock decreased.
     * Guards against going negative due to race conditions.
     */
    public Inventory release(int quantity) {
        int newReserved = Math.max(0, this.reservedStock - quantity);
        return this.toBuilder()
                .reservedStock(newReserved)
                .build();
    }
}
