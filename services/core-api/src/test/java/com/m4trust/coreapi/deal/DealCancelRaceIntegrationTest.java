package com.m4trust.coreapi.deal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.sql.DataSource;

import com.m4trust.coreapi.organization.OperationContext;
import com.m4trust.coreapi.organization.RequestedOperation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Cancel carries no expectedVersion and now locks the Deal row
 * (SELECT ... FOR UPDATE) before a single atomic {@code updateStatus} attempt;
 * the old load-then-retry-once design was replaced by the architect's binding
 * decision to keep lock-based, single-attempt cancel. These tests reproduce
 * the race with a REAL second transaction, in a real second thread, that
 * holds the same row lock a plain concurrent mutation would hold: cancel must
 * block on the lock and then either succeed against the freshly-committed
 * version (a plain rename) or observe the freshly-committed terminal status
 * and reject (a concurrent cancellation-equivalent transition).
 */
@SpringBootTest
@ActiveProfiles("local")
@Testcontainers
@Import(DealCancelRaceIntegrationTest.CountingDealRepositoryConfiguration.class)
class DealCancelRaceIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17.5-alpine");

    @Autowired
    private DealService service;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private AtomicInteger updateStatusAttempts;

    private ExecutorService executor;
    private OperationContext cancelContext;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM spring_session_attributes");
        jdbcTemplate.update("DELETE FROM spring_session");
        jdbcTemplate.execute("""
                TRUNCATE TABLE
                    contract_intelligence_rule_set_version,
                    contract_intelligence_extraction_result_version,
                    contract_intelligence_analysis_job,
                    http_idempotency_record,
                    deal_invitation,
                    deal_participant,
                    document,
                    ratification_package_approval,
                    ratification_package,
                    ratification_package_snapshot,
                    deal,
                    audit_record,
                    legal_entity_membership,
                    legal_entity,
                    tenant_user,
                    tenant,
                    identity_user
                """);
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID legalEntityId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO identity_user (
                    id, email, password_hash, display_name, enabled
                )
                VALUES (?, ?, 'test-hash', 'Race User', true)
                """, userId, "race-deal@example.com");
        jdbcTemplate.update("INSERT INTO tenant (id) VALUES (?)", tenantId);
        jdbcTemplate.update("""
                INSERT INTO tenant_user (user_id, tenant_id)
                VALUES (?, ?)
                """, userId, tenantId);
        jdbcTemplate.update("""
                INSERT INTO legal_entity (
                    id, tenant_id, legal_name, registration_number
                )
                VALUES (?, ?, 'Race Entity', 'RACE-DEAL-1')
                """, legalEntityId, tenantId);
        cancelContext = new OperationContext(userId, tenantId, legalEntityId,
                com.m4trust.coreapi.organization.LegalEntityRole.ADMIN,
                RequestedOperation.DEAL_CANCEL);
        updateStatusAttempts.set(0);
        executor = Executors.newFixedThreadPool(2);
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    void cancelSucceedsWhenOnlyTheVersionChangedConcurrently() throws Exception {
        UUID dealId = createDraftDeal();
        CountDownLatch lockAcquired = new CountDownLatch(1);
        CountDownLatch releaseLock = new CountDownLatch(1);

        Future<Void> racer = executor.submit(() -> {
            lockRowThenRun(dealId, lockAcquired, releaseLock, connection -> {
                try (PreparedStatement update = connection.prepareStatement("""
                        UPDATE deal
                        SET title = 'Concurrently Renamed',
                            version = version + 1
                        WHERE id = ?
                        """)) {
                    update.setObject(1, dealId);
                    update.executeUpdate();
                }
            });
            return null;
        });

        lockAcquired.await(10, TimeUnit.SECONDS);
        Future<DealDetail> cancelFuture = executor.submit(
                () -> service.cancel(cancelContext, dealId, UUID.randomUUID()));
        releaseLock.countDown();

        DealDetail detail = cancelFuture.get(10, TimeUnit.SECONDS);
        racer.get(10, TimeUnit.SECONDS);

        assertEquals(DealStatus.CANCELLED, detail.status());
        assertEquals(1, updateStatusAttempts.get());
        assertEquals("CANCELLED", jdbcTemplate.queryForObject(
                "SELECT deal_status FROM deal WHERE id = ?",
                String.class, dealId));
        assertEquals("Concurrently Renamed", jdbcTemplate.queryForObject(
                "SELECT title FROM deal WHERE id = ?",
                String.class, dealId));
        assertEquals(1, jdbcTemplate.queryForObject("""
                SELECT count(*)
                FROM audit_record
                WHERE subject_id = ? AND action = 'DEAL_CANCELLED'
                """, Integer.class, dealId));
        assertFalse(detail.availableActions().canCancel());
    }

    @Test
    void cancelConflictsWhenTheDealReachedATerminalStateConcurrently() throws Exception {
        UUID dealId = createDraftDeal();
        CountDownLatch lockAcquired = new CountDownLatch(1);
        CountDownLatch releaseLock = new CountDownLatch(1);

        Future<Void> racer = executor.submit(() -> {
            lockRowThenRun(dealId, lockAcquired, releaseLock, connection -> {
                try (PreparedStatement update = connection.prepareStatement("""
                        UPDATE deal
                        SET deal_status = 'CANCELLED',
                            version = version + 1
                        WHERE id = ?
                        """)) {
                    update.setObject(1, dealId);
                    update.executeUpdate();
                }
            });
            return null;
        });

        lockAcquired.await(10, TimeUnit.SECONDS);
        Future<DealDetail> cancelFuture = executor.submit(
                () -> service.cancel(cancelContext, dealId, UUID.randomUUID()));
        releaseLock.countDown();

        try {
            cancelFuture.get(10, TimeUnit.SECONDS);
            throw new AssertionError("Expected cancel to fail against the freshly committed CANCELLED status");
        } catch (java.util.concurrent.ExecutionException expected) {
            assertThrows(DealStateConflictException.class, () -> {
                throw expected.getCause();
            });
        }
        racer.get(10, TimeUnit.SECONDS);

        assertEquals(0, jdbcTemplate.queryForObject("""
                SELECT count(*)
                FROM audit_record
                WHERE subject_id = ? AND action = 'DEAL_CANCELLED'
                """, Integer.class, dealId));
    }

    /**
     * Opens its own transaction on a dedicated JDBC connection, takes the same
     * {@code SELECT ... FOR UPDATE} row lock cancel's load takes, signals the
     * caller once the lock is held, waits for the caller's release signal
     * (issued once cancel has been dispatched and is blocked behind this same
     * lock), then applies the racing mutation and commits - releasing the lock
     * so cancel observes the freshly-committed row.
     */
    private void lockRowThenRun(UUID dealId, CountDownLatch lockAcquired,
            CountDownLatch releaseLock, SqlAction mutation) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement lock = connection.prepareStatement(
                    "SELECT id FROM deal WHERE id = ? FOR UPDATE")) {
                lock.setObject(1, dealId);
                lock.executeQuery();
            }
            lockAcquired.countDown();
            releaseLock.await(10, TimeUnit.SECONDS);
            mutation.run(connection);
            connection.commit();
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }

    private UUID createDraftDeal() {
        OperationContext createContext = new OperationContext(
                cancelContext.authenticatedUserId(),
                cancelContext.tenantId(),
                cancelContext.activeLegalEntityId(),
                cancelContext.activeLegalEntityRole(),
                RequestedOperation.DEAL_CREATE);
        return service.create(createContext,
                new CreateDealRequest("Race Deal", null),
                UUID.randomUUID()).id();
    }

    @FunctionalInterface
    private interface SqlAction {
        void run(Connection connection) throws Exception;
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class CountingDealRepositoryConfiguration {

        @Bean
        AtomicInteger updateStatusAttempts() {
            return new AtomicInteger();
        }

        @Bean
        @Primary
        DealRepository countingDealRepository(JdbcTemplate jdbcTemplate,
                AtomicInteger updateStatusAttempts) {
            return new DealRepository(jdbcTemplate) {
                @Override
                boolean updateStatus(UUID tenantId, UUID legalEntityId,
                        UUID dealId, DealStatus expectedStatus,
                        DealStatus nextStatus, long expectedVersion,
                        Instant updatedAt) {
                    updateStatusAttempts.incrementAndGet();
                    return super.updateStatus(tenantId, legalEntityId, dealId,
                            expectedStatus, nextStatus, expectedVersion,
                            updatedAt);
                }
            };
        }
    }
}
