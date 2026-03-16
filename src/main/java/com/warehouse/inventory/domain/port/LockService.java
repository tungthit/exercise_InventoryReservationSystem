package com.warehouse.inventory.domain.port;



import java.time.Duration;
import java.util.function.Supplier;

/**
 * Output port – infrastructure-agnostic contract for acquiring distributed locks.
 * The Redisson implementation lives in the infrastructure layer.
 */
public interface LockService {

    <T> T executeWithLock(String key, Duration leaseTime, Supplier<T> operation);
}
