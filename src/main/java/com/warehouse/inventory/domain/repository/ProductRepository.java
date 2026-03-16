package com.warehouse.inventory.domain.repository;

import com.warehouse.inventory.domain.model.Product;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.UUID;

public interface ProductRepository extends ReactiveCrudRepository<Product, UUID> {

    Mono<Product> findBySku(String sku);

    @Query("SELECT * FROM products WHERE sku IN (:skus)")
    Flux<Product> findBySkuIn(Collection<String> skus);
}
