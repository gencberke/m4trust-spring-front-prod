package com.m4trust.coreapi.idempotency;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class IdempotencyRepository {

    private final JdbcTemplate jdbcTemplate;

    IdempotencyRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    boolean insertIfAbsent(UUID recordId, IdempotencyRequest request,
            Instant createdAt) {
        return !jdbcTemplate.query("""
                INSERT INTO http_idempotency_record (
                    id,
                    actor_user_id,
                    operation,
                    idempotency_key,
                    canonical_request_hash,
                    created_at
                )
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (actor_user_id, operation, idempotency_key)
                DO NOTHING
                RETURNING id
                """,
                (resultSet, rowNumber) -> resultSet.getObject("id", UUID.class),
                recordId,
                request.actorUserId(),
                request.operation(),
                request.key(),
                request.canonicalRequestHash(),
                Timestamp.from(createdAt))
                .isEmpty();
    }

    Optional<IdempotencyRecord> find(IdempotencyRequest request) {
        return jdbcTemplate.query("""
                SELECT id, canonical_request_hash, result_type, result_id
                FROM http_idempotency_record
                WHERE actor_user_id = ?
                  AND operation = ?
                  AND idempotency_key = ?
                """, this::mapRecord,
                request.actorUserId(), request.operation(), request.key())
                .stream()
                .findFirst();
    }

    boolean recordResult(UUID recordId, IdempotencyResultReference result) {
        return jdbcTemplate.update("""
                UPDATE http_idempotency_record
                SET result_type = ?, result_id = ?
                WHERE id = ?
                  AND result_type IS NULL
                  AND result_id IS NULL
                """, result.type(), result.id(), recordId) == 1;
    }

    private IdempotencyRecord mapRecord(ResultSet resultSet, int rowNumber)
            throws SQLException {
        UUID resultId = resultSet.getObject("result_id", UUID.class);
        Optional<IdempotencyResultReference> resultReference = resultId == null
                ? Optional.empty()
                : Optional.of(new IdempotencyResultReference(
                        resultSet.getString("result_type"), resultId));
        return new IdempotencyRecord(
                resultSet.getObject("id", UUID.class),
                resultSet.getString("canonical_request_hash"), resultReference);
    }
}
