package com.m4trust.coreapi.integration.messaging;

import java.time.Clock;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.Objects;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
class JdbcTransactionalOutbox implements TransactionalOutbox {

    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    JdbcTransactionalOutbox(JdbcTemplate jdbcTemplate, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public UUID enqueue(String eventType, String exchangeName, String routingKey, String payload) {
        requireText(eventType, "eventType");
        requireText(exchangeName, "exchangeName");
        requireText(routingKey, "routingKey");
        Objects.requireNonNull(payload, "payload must not be null");

        UUID id = UUID.randomUUID();
        Instant now = clock.instant();
        jdbcTemplate.update("""
                INSERT INTO integration_outbox_event (
                    id, event_type, exchange_name, routing_key, payload, created_at, available_at
                ) VALUES (?, ?, ?, ?, CAST(? AS jsonb), ?, ?)
                """, id, eventType, exchangeName, routingKey, payload,
                Timestamp.from(now), Timestamp.from(now));
        return id;
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
