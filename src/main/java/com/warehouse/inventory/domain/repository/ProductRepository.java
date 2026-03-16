package com.warehouse.inventory.domain.repository;

import com.warehouse.inventory.domain.model.Product;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductRepository extends CrudRepository<Product, UUID> {

    Optional<Product> findBySku(String sku);

    @Query("SELECT * FROM products WHERE sku IN (:skus)")
    List<Product> findBySkuIn(Collection<String> skus);
}
