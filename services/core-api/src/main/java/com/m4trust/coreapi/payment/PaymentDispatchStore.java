package com.m4trust.coreapi.payment;

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

/**
 * Claims durable dispatch rows outside the caller's business transaction
 * (mirrors {@code JdbcOutboxRelayStore}). The claim/mark-completed pair runs
 * in its own short transaction so the provider call the relay makes in
 * between never runs inside an open database transaction (ADR-010 §2.4).
 */
@Repository
class PaymentDispatchStore {

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final Clock clock;

    PaymentDispatchStore(JdbcTemplate jdbc, TransactionTemplate transactions, Clock clock) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.clock = clock;
    }

    List<DispatchClaim> claimAvailable(int batchSize, Duration claimTimeout) {
        return transactions.execute(status -> {
            Instant now = clock.instant();
            Instant reclaimBefore = now.minus(claimTimeout);
            UUID claimToken = UUID.randomUUID();
            return jdbc.query("""
                    WITH candidates AS (
                        SELECT id FROM payment_dispatch
                        WHERE completed_at IS NULL
                          AND available_at <= ?
                          AND (claimed_at IS NULL OR claimed_at < ?)
                        ORDER BY created_at, id
                        FOR UPDATE SKIP LOCKED
                        LIMIT ?
                    )
                    UPDATE payment_dispatch dispatch
                    SET claimed_at = ?, claim_token = ?
                    FROM candidates
                    WHERE dispatch.id = candidates.id
                    RETURNING dispatch.id, dispatch.claim_token, dispatch.payment_operation_id,
                              dispatch.dispatch_type, dispatch.provider_key, dispatch.amount_minor,
                              dispatch.currency
                    """, (resultSet, rowNum) -> map(resultSet), Timestamp.from(now), Timestamp.from(reclaimBefore),
                    batchSize, Timestamp.from(now), claimToken);
        });
    }

    void markCompleted(DispatchClaim claim) {
        transactions.executeWithoutResult(status -> jdbc.update("""
                UPDATE payment_dispatch
                SET completed_at = ?, claim_token = NULL, claimed_at = NULL
                WHERE id = ? AND claim_token = ? AND completed_at IS NULL
                """, Timestamp.from(clock.instant()), claim.id(), claim.claimToken()));
    }

    private static DispatchClaim map(ResultSet resultSet) throws SQLException {
        return new DispatchClaim(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("claim_token", UUID.class),
                resultSet.getObject("payment_operation_id", UUID.class),
                FundingRepository.DispatchType.valueOf(resultSet.getString("dispatch_type")),
                resultSet.getObject("provider_key", UUID.class),
                resultSet.getLong("amount_minor"),
                resultSet.getString("currency"));
    }

    record DispatchClaim(UUID id, UUID claimToken, UUID paymentOperationId,
            FundingRepository.DispatchType dispatchType, UUID providerKey, long amountMinor, String currency) { }
}
