package com.m4trust.coreapi.organization;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Testcontainers
class LegalEntityIntegrationTest {

    private static final String COOKIE_NAME = "M4TRUST_SESSION";
    private static final String VALID_PASSWORD =
            "a long memorable passphrase";
    private static final String LEGAL_ENTITY_HEADER =
            "X-M4Trust-Legal-Entity-Id";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17.5-alpine");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void cleanDatabase() {
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
    }

    @Test
    void creatorBecomesAdminAndAllPublicProjectionsMatchTheContract()
            throws Exception {
        RegisteredUser creator = register(
                "creator@example.com", "Creator User");
        String correlationId = UUID.randomUUID().toString();

        MvcResult creation = createLegalEntity(
                creator, "  Acme Trading  ", "  REG-100  ", correlationId)
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.*", hasSize(3)))
                .andExpect(jsonPath("$.legalName").value("Acme Trading"))
                .andExpect(jsonPath("$.registrationNumber").value("REG-100"))
                .andReturn();

        UUID legalEntityId = UUID.fromString(JsonPath.read(
                creation.getResponse().getContentAsString(), "$.id"));
        assertEquals("/api/v1/legal-entities/" + legalEntityId,
                creation.getResponse().getHeader("Location"));
        assertEquals("ADMIN", jdbcTemplate.queryForObject("""
                SELECT role
                FROM legal_entity_membership
                WHERE legal_entity_id = ?
                  AND user_id = ?
                """, String.class, legalEntityId, creator.userId()));
        assertEquals(2, jdbcTemplate.queryForObject("""
                SELECT count(*)
                FROM audit_record
                WHERE legal_entity_id = ?
                  AND correlation_id = ?
                """, Integer.class, legalEntityId,
                UUID.fromString(correlationId)));
        List<String> actions = jdbcTemplate.queryForList("""
                SELECT action
                FROM audit_record
                WHERE legal_entity_id = ?
                """, String.class, legalEntityId);
        org.junit.jupiter.api.Assertions.assertTrue(
                actions.containsAll(List.of(
                        "LEGAL_ENTITY_CREATED", "MEMBERSHIP_ASSIGNED")));

        mockMvc.perform(get("/api/v1/auth/me")
                        .cookie(creator.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.*", hasSize(4)))
                .andExpect(jsonPath("$.memberships", hasSize(1)))
                .andExpect(jsonPath(
                        "$.memberships[0].legalEntityId")
                        .value(legalEntityId.toString()))
                .andExpect(jsonPath("$.memberships[0].role")
                        .value("ADMIN"));

        mockMvc.perform(get("/api/v1/legal-entities")
                        .cookie(creator.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.*", hasSize(1)))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].legalName")
                        .value("Acme Trading"));

        mockMvc.perform(get("/api/v1/legal-entities/" + legalEntityId)
                        .cookie(creator.cookie())
                        .header(LEGAL_ENTITY_HEADER, legalEntityId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.*", hasSize(3)))
                .andExpect(jsonPath("$.id")
                        .value(legalEntityId.toString()));

        mockMvc.perform(get("/api/v1/legal-entities/"
                        + legalEntityId + "/members")
                        .cookie(creator.cookie())
                        .header(LEGAL_ENTITY_HEADER, legalEntityId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.*", hasSize(1)))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].userId")
                        .value(creator.userId().toString()))
                .andExpect(jsonPath("$.items[0].email")
                        .value("creator@example.com"))
                .andExpect(jsonPath("$.items[0].displayName")
                        .value("Creator User"))
                .andExpect(jsonPath("$.items[0].role")
                        .value("ADMIN"));
    }

    @Test
    void scopedReadsRejectBadContextAndDoNotDiscloseOtherEntities()
            throws Exception {
        RegisteredUser first = register("first@example.com", "First User");
        UUID firstEntityId = createdId(createLegalEntity(
                first, "First Entity", "FIRST-1",
                UUID.randomUUID().toString())
                .andExpect(status().isCreated())
                .andReturn());

        RegisteredUser second = register(
                "second@example.com", "Second User");
        UUID secondEntityId = createdId(createLegalEntity(
                second, "Second Entity", "SECOND-1",
                UUID.randomUUID().toString())
                .andExpect(status().isCreated())
                .andReturn());

        mockMvc.perform(get("/api/v1/legal-entities/" + firstEntityId)
                        .cookie(second.cookie())
                        .header(LEGAL_ENTITY_HEADER, firstEntityId))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(
                        MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code")
                        .value("LEGAL_ENTITY_NOT_FOUND"))
                .andExpect(jsonPath("$.detail")
                        .value("The legal entity was not found."));

        mockMvc.perform(get("/api/v1/legal-entities/"
                        + firstEntityId + "/members")
                        .cookie(second.cookie())
                        .header(LEGAL_ENTITY_HEADER, firstEntityId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code")
                        .value("LEGAL_ENTITY_NOT_FOUND"))
                .andExpect(jsonPath("$.items").doesNotExist());

        UUID nonexistent = UUID.randomUUID();
        mockMvc.perform(get("/api/v1/legal-entities/" + nonexistent)
                        .cookie(second.cookie())
                        .header(LEGAL_ENTITY_HEADER, nonexistent))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code")
                        .value("LEGAL_ENTITY_NOT_FOUND"));

        mockMvc.perform(get("/api/v1/legal-entities")
                        .cookie(second.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].legalEntityId")
                        .value(secondEntityId.toString()));

        mockMvc.perform(get("/api/v1/legal-entities/" + firstEntityId)
                        .cookie(first.cookie()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code")
                        .value("LEGAL_ENTITY_ACCESS_DENIED"));

        mockMvc.perform(get("/api/v1/legal-entities/" + firstEntityId)
                        .cookie(first.cookie())
                        .header(LEGAL_ENTITY_HEADER, "not-a-uuid"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code")
                        .value("LEGAL_ENTITY_ACCESS_DENIED"));

        mockMvc.perform(get("/api/v1/legal-entities/" + firstEntityId)
                        .cookie(first.cookie())
                        .header(LEGAL_ENTITY_HEADER, secondEntityId))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code")
                        .value("LEGAL_ENTITY_ACCESS_DENIED"));

        mockMvc.perform(get("/api/v1/legal-entities/not-a-uuid")
                        .cookie(first.cookie())
                        .header(LEGAL_ENTITY_HEADER, firstEntityId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("MALFORMED_REQUEST"));
    }

    @Test
    void createValidationUsesTrimmedContractLengths() throws Exception {
        RegisteredUser user = register(
                "validation@example.com", "Validation User");

        createLegalEntity(user, "   ", "REG-1",
                UUID.randomUUID().toString())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field")
                        .value("legalName"));
    }

    private RegisteredUser register(String email, String displayName)
            throws Exception {
        CsrfSession anonymous = csrf(null);
        MvcResult result = mockMvc.perform(
                        post("/api/v1/auth/register")
                                .cookie(anonymous.cookie())
                                .header(anonymous.headerName(),
                                        anonymous.token())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "email": "%s",
                                          "password": "%s",
                                          "displayName": "%s"
                                        }
                                        """.formatted(
                                                email,
                                                VALID_PASSWORD,
                                                displayName)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.*", hasSize(3)))
                .andReturn();
        Cookie cookie = requiredCookie(result);
        UUID userId = UUID.fromString(JsonPath.read(
                result.getResponse().getContentAsString(), "$.id"));
        return new RegisteredUser(userId, cookie, csrf(cookie));
    }

    private org.springframework.test.web.servlet.ResultActions
            createLegalEntity(RegisteredUser user, String legalName,
                    String registrationNumber, String correlationId)
                    throws Exception {
        return mockMvc.perform(post("/api/v1/legal-entities")
                .cookie(user.cookie())
                .header(user.csrf().headerName(), user.csrf().token())
                .header("X-Correlation-ID", correlationId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "legalName": "%s",
                          "registrationNumber": "%s"
                        }
                        """.formatted(legalName, registrationNumber)));
    }

    private UUID createdId(MvcResult result) throws Exception {
        return UUID.fromString(JsonPath.read(
                result.getResponse().getContentAsString(
                        StandardCharsets.UTF_8), "$.id"));
    }

    private CsrfSession csrf(Cookie cookie) throws Exception {
        var request = get("/api/v1/security/csrf");
        if (cookie != null) {
            request.cookie(cookie);
        }
        MvcResult result = mockMvc.perform(request)
                .andExpect(status().isOk())
                .andReturn();
        Cookie resolvedCookie = cookie == null
                ? requiredCookie(result) : cookie;
        String body = result.getResponse().getContentAsString();
        return new CsrfSession(
                resolvedCookie,
                JsonPath.read(body, "$.token"),
                JsonPath.read(body, "$.headerName"));
    }

    private Cookie requiredCookie(MvcResult result) {
        Cookie cookie = result.getResponse().getCookie(COOKIE_NAME);
        assertNotNull(cookie);
        return cookie;
    }

    private record RegisteredUser(
            UUID userId, Cookie cookie, CsrfSession csrf) {
    }

    private record CsrfSession(
            Cookie cookie, String token, String headerName) {
    }
}
