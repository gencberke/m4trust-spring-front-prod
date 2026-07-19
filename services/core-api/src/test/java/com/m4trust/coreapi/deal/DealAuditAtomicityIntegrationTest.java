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
    private DealInvitationService invitationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AtomicBoolean failAudit;

    private OperationContext context;
    private UUID userId;
    private UUID tenantId;
    private UUID legalEntityId;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM spring_session_attributes");
        jdbcTemplate.update("DELETE FROM spring_session");
        jdbcTemplate.execute("""
                TRUNCATE TABLE
                    contract_intelligence_extraction_result_version,
                    contract_intelligence_analysis_job,
                    http_idempotency_record,
                    deal_invitation,
                    deal_participant,
                    document,
                    deal,
                    audit_record,
                    legal_entity_membership,
                    legal_entity,
                    tenant_user,
                    tenant,
                    identity_user
                """);
        userId = UUID.randomUUID();
        tenantId = UUID.randomUUID();
        legalEntityId = UUID.randomUUID();
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
        jdbcTemplate.update("""
                INSERT INTO legal_entity_membership (
                    id, tenant_id, legal_entity_id, user_id, role
                ) VALUES (?, ?, ?, ?, 'ADMIN')
                """, UUID.randomUUID(), tenantId, legalEntityId, userId);
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

    @Test
    void auditFailureRollsBackInvitationAcceptAndParticipant() {
        UUID dealId = UUID.randomUUID();
        UUID invitationId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO deal (
                    id, tenant_id, reference, title, deal_status,
                    initiator_legal_entity_id, created_by
                ) VALUES (?, ?, 'DL-0000000001', 'Atomic invitation Deal',
                    'DRAFT', ?, ?)
                """, dealId, tenantId, legalEntityId, userId);
        jdbcTemplate.update("""
                INSERT INTO deal_invitation (
                    id, tenant_id, deal_id, recipient_email,
                    invitation_status
                ) VALUES (?, ?, ?, 'atomic-deal@example.com', 'PENDING')
                """, invitationId, tenantId, dealId);

        assertThrows(IllegalStateException.class,
                () -> invitationService.accept(userId, invitationId,
                        new AcceptDealInvitationRequest(legalEntityId, 0L),
                        UUID.randomUUID()));

        assertEquals("PENDING", jdbcTemplate.queryForObject("""
                SELECT invitation_status
                FROM deal_invitation
                WHERE id = ?
                """, String.class, invitationId));
        assertEquals(0, count("deal_participant"));
        assertEquals(0, count("audit_record"));
    }

    @Test
    void auditFailureRollsBackPartyAssignmentAndVersion() {
        UUID dealId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO deal (
                    id, tenant_id, reference, title, deal_status,
                    initiator_legal_entity_id, created_by
                ) VALUES (?, ?, 'DL-0000000002', 'Atomic party Deal',
                    'DRAFT', ?, ?)
                """, dealId, tenantId, legalEntityId, userId);
        jdbcTemplate.update("""
                INSERT INTO deal_participant (
                    deal_id, tenant_id, legal_entity_id,
                    legal_entity_tenant_id
                ) VALUES (?, ?, ?, ?)
                """, dealId, tenantId, legalEntityId, tenantId);
        UpdateDealPartiesRequest request = new UpdateDealPartiesRequest();
        request.setBuyerLegalEntityId(legalEntityId);
        request.setSellerLegalEntityId(null);
        request.setExpectedVersion(0L);
        OperationContext partyContext = new OperationContext(userId, tenantId,
                legalEntityId, RequestedOperation.DEAL_PARTIES_UPDATE);

        assertThrows(IllegalStateException.class,
                () -> service.updateParties(partyContext, dealId, request,
                        UUID.randomUUID()));

        assertEquals(0L, jdbcTemplate.queryForObject("""
                SELECT version FROM deal WHERE id = ?
                """, Long.class, dealId));
        assertEquals(0, jdbcTemplate.queryForObject("""
                SELECT count(*) FROM deal
                WHERE id = ? AND buyer_legal_entity_id IS NOT NULL
                """, Integer.class, dealId));
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
