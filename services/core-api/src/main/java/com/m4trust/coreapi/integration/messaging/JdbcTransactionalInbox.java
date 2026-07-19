package com.m4trust.coreapi.integration.messaging;

import java.time.Clock;
import java.util.Objects;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcTransactionalInbox implements TransactionalInbox {

    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    JdbcTransactionalInbox(JdbcTemplate jdbcTemplate, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
    }

    @Override
    public boolean recordIfNew(UUID eventId, String eventType) {
        Objects.requireNonNull(eventId, "eventId must not be null");
        if (eventType == null || eventType.isBlank()) {
            throw new IllegalArgumentException("eventType must not be blank");
        }
        return jdbcTemplate.update("""
                INSERT INTO integration_inbox_event (event_id, event_type, received_at)
                VALUES (?, ?, ?)
                ON CONFLICT (event_id) DO NOTHING
                """, eventId, eventType, clock.instant()) == 1;
    }
}
