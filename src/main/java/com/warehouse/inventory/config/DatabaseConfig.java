package com.warehouse.inventory.config;

import io.r2dbc.spi.ConnectionFactory;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.data.r2dbc.config.EnableR2dbcAuditing;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.transaction.ReactiveTransactionManager;

/**
 * R2DBC (reactive) database config + Flyway bootstrap.
 *
 * Flyway runs on the blocking JDBC datasource (autoconfigured from
 * spring.datasource.*) before any R2DBC repository is used.
 * The @DependsOn on ReactiveTransactionManager ensures this ordering.
 */
@Configuration
@EnableR2dbcRepositories(basePackages = "com.warehouse.inventory.domain.repository")
@EnableR2dbcAuditing
public class DatabaseConfig {

    /**
     * Explicit JDBC-backed Flyway bean so we control the lifecycle order.
     */
    @Bean(initMethod = "migrate")
    public Flyway flyway(
            @Value("${spring.datasource.url}") String url,
            @Value("${spring.datasource.username}") String username,
            @Value("${spring.datasource.password}") String password) {
        return Flyway.configure()
                .dataSource(url, username, password)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .load();
    }

    /**
     * Reactive transaction manager required by @Transactional on
     * Mono/Flux returning service methods.
     */
    @Bean
    @DependsOn("flyway")
    public ReactiveTransactionManager reactiveTransactionManager(ConnectionFactory connectionFactory) {
        return new R2dbcTransactionManager(connectionFactory);
    }
}
