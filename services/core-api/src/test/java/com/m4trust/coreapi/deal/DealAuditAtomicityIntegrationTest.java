package com.m4trust.coreapi.deal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import com.m4trust.coreapi.audit.AuditAppendPort;
import com.m4trust.coreapi.organization.OperationContext;
import com.m4trust.coreapi.organization.RequestedOperation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
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

@SpringBootTest
@ActiveProfiles("local")
@Testcontainers
@Import(DealAuditAtomicityIntegrationTest.FailingAuditConfiguration.class)
class DealAuditAtomicityIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17.5-alpine");

    @Autowired
    private DealService service;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AtomicBoolean failAudit;

    private OperationContext context;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM spring_session_attributes");
        jdbcTemplate.update("DELETE FROM spring_session");
        jdbcTemplate.execute("""
                TRUNCATE TABLE
                    http_idempotency_record,
                    deal_participant,
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
                VALUES (?, ?, 'test-hash', 'Atomic User', true)
                """, userId, "atomic-deal@example.com");
        jdbcTemplate.update("INSERT INTO tenant (id) VALUES (?)", tenantId);
        jdbcTemplate.update("""
                INSERT INTO tenant_user (user_id, tenant_id)
                VALUES (?, ?)
                """, userId, tenantId);
        jdbcTemplate.update("""
                INSERT INTO legal_entity (
                    id, tenant_id, legal_name, registration_number
                )
                VALUES (?, ?, 'Atomic Entity', 'ATOMIC-DEAL-1')
                """, legalEntityId, tenantId);
        context = new OperationContext(userId, tenantId, legalEntityId,
                RequestedOperation.DEAL_CREATE);
        failAudit.set(true);
    }

    @Test
    void auditFailureRollsBackDealAndInitialParticipant() {
        assertThrows(IllegalStateException.class,
                () -> service.create(
                        context,
                        new CreateDealRequest("Atomic Deal", null),
                        UUID.randomUUID()));

        assertEquals(0, count("deal"));
        assertEquals(0, count("deal_participant"));
        assertEquals(0, count("audit_record"));
    }

    private int count(String table) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM " + table, Integer.class);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FailingAuditConfiguration {

        @Bean
        AtomicBoolean failAudit() {
            return new AtomicBoolean();
        }

        @Bean
        @Primary
        AuditAppendPort failingAuditAppender(
                @Qualifier("jdbcAuditAppender") AuditAppendPort delegate,
                AtomicBoolean failAudit) {
            return record -> {
                if (failAudit.get()) {
                    throw new IllegalStateException(
                            "test audit append failure");
                }
                delegate.append(record);
            };
        }
    }
}
