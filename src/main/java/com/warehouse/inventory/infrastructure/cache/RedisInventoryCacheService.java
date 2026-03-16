package com.warehouse.inventory.infrastructure.cache;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;


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

    private final org.springframework.data.redis.core.StringRedisTemplate redisTemplate;

    public Integer getAvailableStock(String sku) {
        try {
            String value = redisTemplate.opsForValue().get(KEY_PREFIX + sku);
            if (value != null) {
                log.debug("Cache HIT for SKU '{}': {}", sku, value);
                return Integer.parseInt(value);
            }
        } catch (Exception e) {
            log.warn("Cache GET error for SKU '{}': {}", sku, e.getMessage());
        }
        return null;
    }

    public void setAvailableStock(String sku, int availableStock) {
        try {
            redisTemplate.opsForValue().set(KEY_PREFIX + sku, String.valueOf(availableStock), TTL);
            log.debug("Cache SET for SKU '{}': {}", sku, availableStock);
        } catch (Exception e) {
            log.warn("Cache SET error for SKU '{}': {}", sku, e.getMessage());
        }
    }

    public void evict(String sku) {
        try {
            Boolean deleted = redisTemplate.delete(KEY_PREFIX + sku);
            log.debug("Cache EVICT for SKU '{}', removed={}", sku, deleted);
        } catch (Exception e) {
            log.warn("Cache EVICT error for SKU '{}': {}", sku, e.getMessage());
        }
    }
}
