package com.warehouse.inventory.domain.repository;

import com.warehouse.inventory.domain.model.Inventory;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;

import java.util.Optional;
import java.util.UUID;

public interface InventoryRepository extends CrudRepository<Inventory, UUID> {

    /**
     * Normal read (may be served from cache).
     */
    @Query("""
            SELECT i.id, i.product_id, i.total_stock, i.reserved_stock, i.version
            FROM inventory i
            INNER JOIN products p ON i.product_id = p.id
            WHERE p.sku = :sku
            """)
    Optional<Inventory> findByProductSku(String sku);

    /**
     * Pessimistic write lock – must be called inside an active transaction.
     * {@code FOR UPDATE} blocks concurrent writers on the same row until
     * the transaction commits or rolls back.
     */
    @Query("""
            SELECT i.id, i.product_id, i.total_stock, i.reserved_stock, i.version
            FROM inventory i
            INNER JOIN products p ON i.product_id = p.id
            WHERE p.sku = :sku
            FOR UPDATE
            """)
    Optional<Inventory> findByProductSkuForUpdate(String sku);
}
