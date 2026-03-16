package com.warehouse.inventory.infrastructure.lock;

import com.warehouse.inventory.domain.port.LockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLockReactive;
import org.redisson.api.RedissonReactiveClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

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

    private final RedissonReactiveClient redissonReactiveClient;

    @Override
    public <T> Mono<T> executeWithLock(String key, Duration leaseTime, Supplier<Mono<T>> operation) {
        RLockReactive lock = redissonReactiveClient.getLock(key);

        return lock.tryLock(0, leaseTime.toSeconds(), TimeUnit.SECONDS)
                .flatMap(acquired -> {
                    if (!acquired) {
                        log.warn("Could not acquire distributed lock for key: {}", key);
                        return Mono.error(new LockAcquisitionException(
                                "Could not acquire lock for: " + key));
                    }
                    log.debug("Acquired distributed lock: {}", key);
                    return operation.get()
                            .doFinally(signal -> {
                                log.debug("Releasing distributed lock: {} (signal={})", key, signal);
                                // forceUnlock() is required in reactive pipelines: doFinally may run on
                                // a different Netty IO thread than the one that acquired the lock.
                                // Redisson's unlock() validates thread-id ownership and throws if mismatched.
                                lock.forceUnlock().subscribe(
                                        released -> log.debug("Lock {} force-released={}", key, released),
                                        err -> log.warn("Error releasing lock {}: {}", key, err.getMessage()));
                            });
                });
    }
}
