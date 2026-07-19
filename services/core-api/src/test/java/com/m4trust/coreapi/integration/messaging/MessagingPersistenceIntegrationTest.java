package com.m4trust.coreapi.integration.messaging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = {
        "app.messaging.topology.enabled=false",
        "app.messaging.relay.enabled=false"
})
@ActiveProfiles("local")
@Testcontainers
class MessagingPersistenceIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17.5-alpine");

    @Autowired
    private TransactionalOutbox outbox;

    @Autowired
    private TransactionalInbox inbox;

    @Autowired
    private JdbcOutboxRelayStore relayStore;

    @Autowired
    private TransactionTemplate transactions;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update("DELETE FROM integration_outbox_event");
        jdbcTemplate.update("DELETE FROM integration_inbox_event");
        jdbcTemplate.update("DELETE FROM tenant");
    }

    @Test
    void outboxEventRollsBackWithOwningBusinessTransaction() {
        UUID tenantId = UUID.randomUUID();

        assertThrows(IllegalStateException.class, () -> transactions.executeWithoutResult(status -> {
            jdbcTemplate.update("INSERT INTO tenant (id) VALUES (?)", tenantId);
            outbox.enqueue("ai.document-extraction.requested.v1", "m4trust.ai.commands",
                    "ai.document-extraction.requested.v1", "{}");
            throw new IllegalStateException("force owning transaction rollback");
        }));

        assertEquals(0, count("tenant"));
        assertEquals(0, count("integration_outbox_event"));
    }

    @Test
    void outboxAndInboxRequireTheOwningBusinessTransaction() {
        assertThrows(IllegalTransactionStateException.class, () -> outbox.enqueue(
                "ai.document-extraction.requested.v1", "m4trust.ai.commands",
                "ai.document-extraction.requested.v1", "{}"));
        assertThrows(IllegalTransactionStateException.class, () -> inbox.recordIfNew(
                UUID.randomUUID(), "ai.document-extraction.completed.v1"));
    }

    @Test
    void duplicateInboxEventDoesNotApplyBusinessMutationTwice() {
        UUID eventId = UUID.randomUUID();
        UUID firstTenantId = UUID.randomUUID();
        UUID secondTenantId = UUID.randomUUID();

        transactions.executeWithoutResult(status -> {
            assertTrue(inbox.recordIfNew(eventId, "ai.document-extraction.completed.v1"));
            jdbcTemplate.update("INSERT INTO tenant (id) VALUES (?)", firstTenantId);
        });
        transactions.executeWithoutResult(status -> {
            boolean applyBusinessMutation = inbox.recordIfNew(eventId,
                    "ai.document-extraction.completed.v1");
            assertFalse(applyBusinessMutation);
            if (applyBusinessMutation) {
                jdbcTemplate.update("INSERT INTO tenant (id) VALUES (?)", secondTenantId);
            }
        });

        assertEquals(1, count("tenant"));
        assertEquals(1, count("integration_inbox_event"));
    }

    @Test
    void claimedOutboxEventCanBeMarkedPublished() {
        UUID eventId = transactions.execute(status -> outbox.enqueue(
                "ai.document-extraction.requested.v1", "m4trust.ai.commands",
                "ai.document-extraction.requested.v1", "{}"));

        List<OutboxClaim> claims = relayStore.claimAvailable(10, Duration.ofMinutes(1));

        assertEquals(1, claims.size());
        assertEquals(eventId, claims.getFirst().id());
        relayStore.markPublished(claims.getFirst());
        assertEquals(1, jdbcTemplate.queryForObject("""
                SELECT count(*) FROM integration_outbox_event
                WHERE id = ? AND published_at IS NOT NULL
                """, Integer.class, eventId));
    }

    private int count(String table) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }
}
