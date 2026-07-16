package com.m4trust.coreapi.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.UUID;

import com.m4trust.coreapi.organization.TenantProvisioningPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@ActiveProfiles("local")
@Testcontainers
class RegistrationTransactionIntegrationTest {

    private static final String VALID_PASSWORD = "a long memorable passphrase";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17.5-alpine");

    @Autowired
    private IdentityService identityService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private TenantProvisioningPort tenantProvisioning;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute("""
                TRUNCATE TABLE
                    audit_record,
                    legal_entity_membership,
                    legal_entity,
                    tenant_user,
                    tenant,
                    identity_user
                """);
    }

    @Test
    void tenantProvisioningFailureRollsBackIdentityInsert() {
        when(tenantProvisioning.provisionForNewUser(any(UUID.class)))
                .thenThrow(new IllegalStateException("test provisioning failure"));

        assertThrows(IllegalStateException.class, () -> identityService.register(
                "rollback@example.com", VALID_PASSWORD, "Rollback User"));

        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT count(*) FROM identity_user WHERE email = ?",
                Integer.class, "rollback@example.com"));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT count(*) FROM tenant",
                Integer.class));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT count(*) FROM tenant_user",
                Integer.class));
    }
}
