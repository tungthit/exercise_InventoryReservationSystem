package com.warehouse.inventory.config;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.api.RedissonReactiveClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;

import java.time.Duration;

@Configuration
public class RedisConfig {

    @Value("${redisson.address:redis://localhost:6379}")
    private String redissonAddress;

    @Value("${redisson.connection-pool-size:64}")
    private int connectionPoolSize;

    @Value("${redisson.connection-minimum-idle-size:8}")
    private int minIdleSize;

    @Value("${redisson.lock-watchdog-timeout-ms:30000}")
    private long watchdogTimeoutMs;

    /**
     * Redisson client for distributed locking.
     * Configured independently of Spring Data Redis.
     */
    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient() {
        Config config = new Config();
        config.useSingleServer()
                .setAddress(redissonAddress)
                .setConnectionPoolSize(connectionPoolSize)
                .setConnectionMinimumIdleSize(minIdleSize)
                .setConnectTimeout(3000)
                .setTimeout(3000);
        config.setLockWatchdogTimeout(watchdogTimeoutMs);
        return Redisson.create(config);
    }

    /** Reactive facade over the Redisson client. */
    @Bean
    public RedissonReactiveClient redissonReactiveClient(RedissonClient redissonClient) {
        return redissonClient.reactive();
    }

    /** Convenient reactive string template for cache operations. */
    @Bean
    public ReactiveStringRedisTemplate reactiveStringRedisTemplate(
            ReactiveRedisConnectionFactory factory) {
        return new ReactiveStringRedisTemplate(factory);
    }
}
