package com.warehouse.inventory.config;

import io.nats.client.*;
import io.nats.client.api.RetentionPolicy;
import io.nats.client.api.StorageType;
import io.nats.client.api.StreamConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.time.Duration;

@Slf4j
@Configuration
public class NatsConfig {

    @Value("${nats.url:nats://localhost:4222}")
    private String natsUrl;

    @Value("${nats.reconnect-wait-ms:1000}")
    private long reconnectWaitMs;

    @Value("${nats.max-reconnects:-1}")
    private int maxReconnects;

    @Value("${nats.connection-timeout-ms:5000}")
    private long connectionTimeoutMs;

    /** NATS core connection with auto-reconnect. */
    @Bean
    public Connection natsConnection() throws IOException, InterruptedException {
        Options options = new Options.Builder()
                .server(natsUrl)
                .connectionTimeout(Duration.ofMillis(connectionTimeoutMs))
                .reconnectWait(Duration.ofMillis(reconnectWaitMs))
                .maxReconnects(maxReconnects)
                .pingInterval(Duration.ofSeconds(10))
                .connectionListener((conn, type) ->
                        log.info("NATS connection event: {}", type))
                .errorListener(new ErrorListener() {
                    @Override
                    public void errorOccurred(Connection conn, String error) {
                        log.error("NATS error: {}", error);
                    }
                })
                .build();
        return Nats.connect(options);
    }

    /** JetStream context (durable, at-least-once delivery). */
    @Bean
    public JetStream jetStream(Connection connection) throws IOException {
        JetStreamOptions jsOptions = JetStreamOptions.builder()
                .publishNoAck(false)     // wait for PubAck to confirm delivery
                .build();
        return connection.jetStream(jsOptions);
    }

    /**
     * Ensure the RESERVATIONS stream exists (idempotent create-or-update).
     * Subject pattern: reservations.> captures all sub-subjects.
     */
    @Bean
    public StreamConfiguration reservationStream(Connection connection) throws IOException {
        JetStreamManagement jsm = connection.jetStreamManagement();

        StreamConfiguration cfg = StreamConfiguration.builder()
                .name("RESERVATIONS")
                .subjects("reservations.>")
                .storageType(StorageType.File)
                .retentionPolicy(RetentionPolicy.Limits)
                .maxAge(Duration.ofDays(7))
                .replicas(1)
                .build();
        try {
            jsm.updateStream(cfg);
            log.info("NATS JetStream stream 'RESERVATIONS' updated");
        } catch (Exception e) {
            try {
                jsm.addStream(cfg);
                log.info("NATS JetStream stream 'RESERVATIONS' created");
            } catch (Exception ex) {
                log.warn("Could not create stream, it may already exist: {}", ex.getMessage());
            }
        }
        return cfg;
    }
}
