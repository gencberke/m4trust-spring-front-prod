package com.m4trust.coreapi.organization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import com.m4trust.coreapi.audit.AuditAppendPort;
import com.m4trust.coreapi.identity.IdentityService;
import com.m4trust.coreapi.identity.PublicUser;
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
@Import(LegalEntityAuditAtomicityIntegrationTest.FailingAuditConfiguration.class)
class LegalEntityAuditAtomicityIntegrationTest {

    private static final String VALID_PASSWORD =
            "a long memorable passphrase";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17.5-alpine");

    @Autowired
    private IdentityService identityService;

    @Autowired
    private LegalEntityService legalEntityService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AtomicInteger auditInvocationCount;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update("DELETE FROM spring_session_attributes");
        jdbcTemplate.update("DELETE FROM spring_session");
        jdbcTemplate.execute("""
                TRUNCATE TABLE
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
        auditInvocationCount.set(0);
    }

    @Test
    void secondAuditFailureRollsBackEntityMembershipAndFirstAudit() {
        PublicUser user = identityService.register(
                "atomicity@example.com",
                VALID_PASSWORD,
                "Atomicity User");

        assertThrows(IllegalStateException.class,
                () -> legalEntityService.create(
                        user.id(),
                        new CreateLegalEntityRequest(
                                "Atomic Entity", "ATOMIC-1"),
                        UUID.randomUUID()));

        assertEquals(0, count("legal_entity"));
        assertEquals(0, count("legal_entity_membership"));
        assertEquals(0, count("audit_record"));
    }

    private int count(String table) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM " + table, Integer.class);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FailingAuditConfiguration {

        @Bean
        AtomicInteger auditInvocationCount() {
            return new AtomicInteger();
        }

        @Bean
        @Primary
        AuditAppendPort failingSecondAuditAppender(
                @Qualifier("jdbcAuditAppender")
                AuditAppendPort delegate,
                AtomicInteger invocationCount) {
            return record -> {
                if (invocationCount.incrementAndGet() == 2) {
                    throw new IllegalStateException(
                            "test second audit append failure");
                }
                delegate.append(record);
            };
        }
    }
}
