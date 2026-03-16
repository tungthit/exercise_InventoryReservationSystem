package com.warehouse.inventory.infrastructure.cache;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Cache-aside pattern for inventory available-stock counts.
 *
 * <p>The cache is evicted on every write operation (reserve / release) so
 * subsequent reads re-populate from the DB. A 5-minute TTL prevents stale
 * cache entries from accumulating if evict is missed.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisInventoryCacheService {

    private static final String   KEY_PREFIX = "inventory:available:";
    private static final Duration TTL        = Duration.ofMinutes(5);

    private final ReactiveStringRedisTemplate redisTemplate;

    public Mono<Integer> getAvailableStock(String sku) {
        return redisTemplate.opsForValue()
                .get(KEY_PREFIX + sku)
                .map(Integer::parseInt)
                .doOnNext(v -> log.debug("Cache HIT for SKU '{}': {}", sku, v))
                .doOnError(e -> log.warn("Cache GET error for SKU '{}': {}", sku, e.getMessage()))
                .onErrorResume(e -> Mono.empty());
    }

    public Mono<Void> setAvailableStock(String sku, int availableStock) {
        return redisTemplate.opsForValue()
                .set(KEY_PREFIX + sku, String.valueOf(availableStock), TTL)
                .doOnSuccess(b -> log.debug("Cache SET for SKU '{}': {}", sku, availableStock))
                .then();
    }

    public Mono<Void> evict(String sku) {
        return redisTemplate.delete(KEY_PREFIX + sku)
                .doOnSuccess(deleted -> log.debug("Cache EVICT for SKU '{}', removed={}", sku, deleted))
                .then();
    }
}
