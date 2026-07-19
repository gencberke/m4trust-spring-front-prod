package com.m4trust.coreapi.deal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.m4trust.coreapi.organization.OperationContext;
import com.m4trust.coreapi.organization.RequestedOperation;
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
 * Cancel carries no expectedVersion, so a plain concurrent version bump must
 * not fail it, while a concurrent transition into a terminal state must keep
 * producing a state conflict. The race window between the service's load and
 * its atomic status update is reproduced deterministically with a repository
 * hook that runs a competing SQL mutation first.
 */
@SpringBootTest
@ActiveProfiles("local")
@Testcontainers
@Import(DealCancelRaceIntegrationTest.RacingDealRepositoryConfiguration.class)
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
    private AtomicReference<Runnable> beforeUpdateStatusHook;

    @Autowired
    private AtomicInteger updateStatusAttempts;

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
                RequestedOperation.DEAL_CANCEL);
        beforeUpdateStatusHook.set(null);
        updateStatusAttempts.set(0);
    }

    @Test
    void cancelSucceedsWhenOnlyTheVersionChangedConcurrently() {
        UUID dealId = createDraftDeal();
        beforeUpdateStatusHook.set(() -> jdbcTemplate.update("""
                UPDATE deal
                SET title = 'Concurrently Renamed',
                    version = version + 1
                WHERE id = ?
                """, dealId));

        DealDetail detail = service.cancel(
                cancelContext, dealId, UUID.randomUUID());

        assertEquals(DealStatus.CANCELLED, detail.status());
        assertEquals(2, updateStatusAttempts.get());
        assertEquals("CANCELLED", jdbcTemplate.queryForObject(
                "SELECT deal_status FROM deal WHERE id = ?",
                String.class, dealId));
        assertEquals(1, jdbcTemplate.queryForObject("""
                SELECT count(*)
                FROM audit_record
                WHERE subject_id = ? AND action = 'DEAL_CANCELLED'
                """, Integer.class, dealId));
        assertFalse(detail.availableActions().canCancel());
    }

    @Test
    void cancelConflictsWhenTheDealReachedATerminalStateConcurrently() {
        UUID dealId = createDraftDeal();
        beforeUpdateStatusHook.set(() -> jdbcTemplate.update("""
                UPDATE deal
                SET deal_status = 'CANCELLED',
                    version = version + 1
                WHERE id = ?
                """, dealId));

        assertThrows(DealStateConflictException.class,
                () -> service.cancel(cancelContext, dealId, UUID.randomUUID()));

        assertEquals(0, jdbcTemplate.queryForObject("""
                SELECT count(*)
                FROM audit_record
                WHERE subject_id = ? AND action = 'DEAL_CANCELLED'
                """, Integer.class, dealId));
    }

    private UUID createDraftDeal() {
        OperationContext createContext = new OperationContext(
                cancelContext.authenticatedUserId(),
                cancelContext.tenantId(),
                cancelContext.activeLegalEntityId(),
                RequestedOperation.DEAL_CREATE);
        return service.create(createContext,
                new CreateDealRequest("Race Deal", null),
                UUID.randomUUID()).id();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class RacingDealRepositoryConfiguration {

        @Bean
        AtomicReference<Runnable> beforeUpdateStatusHook() {
            return new AtomicReference<>();
        }

        @Bean
        AtomicInteger updateStatusAttempts() {
            return new AtomicInteger();
        }

        @Bean
        @Primary
        DealRepository racingDealRepository(JdbcTemplate jdbcTemplate,
                AtomicReference<Runnable> beforeUpdateStatusHook,
                AtomicInteger updateStatusAttempts) {
            return new DealRepository(jdbcTemplate) {
                @Override
                boolean updateStatus(UUID tenantId, UUID legalEntityId,
                        UUID dealId, DealStatus expectedStatus,
                        DealStatus nextStatus, long expectedVersion,
                        Instant updatedAt) {
                    updateStatusAttempts.incrementAndGet();
                    Runnable hook = beforeUpdateStatusHook.getAndSet(null);
                    if (hook != null) {
                        hook.run();
                    }
                    return super.updateStatus(tenantId, legalEntityId, dealId,
                            expectedStatus, nextStatus, expectedVersion,
                            updatedAt);
                }
            };
        }
    }
}
