package com.m4trust.coreapi.integration.messaging;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

@Repository
class JdbcOutboxRelayStore {

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactions;
    private final Clock clock;

    JdbcOutboxRelayStore(JdbcTemplate jdbcTemplate, TransactionTemplate transactions, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactions = transactions;
        this.clock = clock;
    }

    List<OutboxClaim> claimAvailable(int batchSize, Duration claimTimeout) {
        return transactions.execute(status -> {
            Instant now = clock.instant();
            Instant reclaimBefore = now.minus(claimTimeout);
            UUID claimToken = UUID.randomUUID();
            return jdbcTemplate.query("""
                    WITH candidates AS (
                        SELECT id FROM integration_outbox_event
                        WHERE published_at IS NULL
                          AND available_at <= ?
                          AND (claimed_at IS NULL OR claimed_at < ?)
                        ORDER BY created_at, id
                        FOR UPDATE SKIP LOCKED
                        LIMIT ?
                    )
                    UPDATE integration_outbox_event event
                    SET claimed_at = ?, claim_token = ?,
                        publish_attempt_count = publish_attempt_count + 1
                    FROM candidates
                    WHERE event.id = candidates.id
                    RETURNING event.id, event.claim_token, event.exchange_name,
                              event.routing_key, event.payload::text
                    """, (resultSet, rowNum) -> map(resultSet), Timestamp.from(now),
                    Timestamp.from(reclaimBefore), batchSize, Timestamp.from(now), claimToken);
        });
    }

    void markPublished(OutboxClaim claim) {
        transactions.executeWithoutResult(status -> jdbcTemplate.update("""
                UPDATE integration_outbox_event
                SET published_at = ?, claim_token = NULL, claimed_at = NULL
                WHERE id = ? AND claim_token = ? AND published_at IS NULL
                """, Timestamp.from(clock.instant()), claim.id(), claim.claimToken()));
    }

    private static OutboxClaim map(ResultSet resultSet) throws SQLException {
        return new OutboxClaim(resultSet.getObject("id", UUID.class),
                resultSet.getObject("claim_token", UUID.class), resultSet.getString("exchange_name"),
                resultSet.getString("routing_key"), resultSet.getString("payload"));
    }
}
