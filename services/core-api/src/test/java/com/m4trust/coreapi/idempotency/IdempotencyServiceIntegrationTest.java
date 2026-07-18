package com.m4trust.coreapi.idempotency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@ActiveProfiles("local")
@Testcontainers
class IdempotencyServiceIntegrationTest {

    private static final String CREATE_INVITATION = "DEAL_INVITATION_CREATE";
    private static final String REQUEST_HASH = "a".repeat(64);

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17.5-alpine");

    @Autowired
    private IdempotencyService service;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private UUID actorUserId;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("TRUNCATE TABLE http_idempotency_record, identity_user CASCADE");
        actorUserId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO identity_user (
                    id, email, password_hash, display_name, enabled
                )
                VALUES (?, ?, 'test-hash', 'Idempotency User', true)
                """, actorUserId, "idempotency@example.com");
    }

    @Test
    void sameRequestReturnsTheRecordedResultReferenceOnReplay() {
        UUID key = UUID.randomUUID();
        IdempotencyResultReference result = new IdempotencyResultReference(
                "DEAL_INVITATION", UUID.randomUUID());

        IdempotencyClaim firstClaim = inTransaction(() -> {
            IdempotencyClaim claim = service.claim(request(key, REQUEST_HASH));
            service.recordResult(claim, result);
            return claim;
        });
        IdempotencyClaim replay = inTransaction(
                () -> service.claim(request(key, REQUEST_HASH)));

        assertEquals(IdempotencyClaimStatus.CLAIMED, firstClaim.status());
        assertTrue(replay.isReplay());
        assertEquals(result, replay.resultReference());
        assertEquals(1, recordCount());
    }

    @Test
    void sameKeyWithDifferentCanonicalRequestConflicts() {
        UUID key = UUID.randomUUID();
        inTransaction(() -> {
            IdempotencyClaim claim = service.claim(request(key, REQUEST_HASH));
            service.recordResult(claim, new IdempotencyResultReference(
                    "DEAL_INVITATION", UUID.randomUUID()));
            return null;
        });

        assertThrows(IdempotencyKeyReusedException.class,
                () -> inTransaction(() -> service.claim(request(key,
                        "b".repeat(64)))));
        assertEquals(1, recordCount());
    }

    @Test
    void rolledBackBusinessTransactionDoesNotConsumeTheKey() {
        UUID key = UUID.randomUUID();

        assertThrows(IllegalStateException.class, () -> inTransaction(() -> {
            service.claim(request(key, REQUEST_HASH));
            throw new IllegalStateException("business mutation failed");
        }));

        assertEquals(0, recordCount());
        IdempotencyClaim retry = inTransaction(
                () -> service.claim(request(key, REQUEST_HASH)));
        assertFalse(retry.isReplay());
    }

    private IdempotencyRequest request(UUID key, String canonicalRequestHash) {
        return new IdempotencyRequest(actorUserId, CREATE_INVITATION, key,
                canonicalRequestHash);
    }

    private int recordCount() {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM http_idempotency_record", Integer.class);
    }

    private <T> T inTransaction(Supplier<T> action) {
        return new TransactionTemplate(transactionManager).execute(
                ignored -> action.get());
    }
}
