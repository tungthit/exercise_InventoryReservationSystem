package com.warehouse.inventory.infrastructure.lock;

import com.warehouse.inventory.domain.port.LockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;


import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Redisson-backed implementation of {@link LockService}.
 *
 * <p>Uses {@code tryLock(waitTime=0, leaseTime, unit)} so callers fail-fast
 * instead of queuing behind a long back-pressure wait. The distributed lock
 * is always released in a {@code doFinally} signal to cover both normal
 * completion and errors.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedissonLockService implements LockService {

    private final org.redisson.api.RedissonClient redissonClient;

    @Override
    public <T> T executeWithLock(String key, Duration leaseTime, Supplier<T> operation) {
        org.redisson.api.RLock lock = redissonClient.getLock(key);

        boolean acquired;
        try {
            acquired = lock.tryLock(0, leaseTime.toSeconds(), TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LockAcquisitionException("Interrupted while acquiring lock for: " + key);
        }

        if (!acquired) {
            log.warn("Could not acquire distributed lock for key: {}", key);
            throw new LockAcquisitionException("Could not acquire lock for: " + key);
        }

        log.debug("Acquired distributed lock: {}", key);
        try {
            return operation.get();
        } finally {
            log.debug("Releasing distributed lock: {}", key);
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
