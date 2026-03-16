package com.warehouse.inventory.domain.port;

import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Output port – infrastructure-agnostic contract for acquiring distributed locks.
 * The Redisson implementation lives in the infrastructure layer.
 */
public interface LockService {

    /**
     * Acquires the named lock, executes the operation, and releases the lock.
     *
     * @param key       unique lock identifier (e.g. "inventory:lock:SKU-001")
     * @param leaseTime maximum duration the lock is held
     * @param operation the reactive operation to execute under the lock
     */
    <T> Mono<T> executeWithLock(String key, Duration leaseTime, Supplier<Mono<T>> operation);
}
