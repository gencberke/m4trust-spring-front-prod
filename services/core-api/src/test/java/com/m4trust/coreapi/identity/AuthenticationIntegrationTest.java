package com.m4trust.coreapi.identity;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import java.util.Base64;
import java.nio.charset.StandardCharsets;

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

@SpringBootTest(properties = {
        "app.environment=test",
        "app.version=test-release",
        "app.git-commit-sha=0123456789abcdef",
        "app.build-time=2026-07-17T12:00:00Z"
})
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Testcontainers
class AuthenticationIntegrationTest {

    private static final String COOKIE_NAME = "M4TRUST_SESSION";
    private static final String VALID_PASSWORD = "a long memorable passphrase";

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
                    http_idempotency_record,
                    deal_invitation,
                    deal_participant,
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
    void privateInfoCarriesGenericReleaseIdentity() throws Exception {
        mockMvc.perform(get("/actuator/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.app.environment").value("test"))
                .andExpect(jsonPath("$.app.version").value("test-release"))
                .andExpect(jsonPath("$.app.gitCommitSha").value("0123456789abcdef"))
                .andExpect(jsonPath("$.app.buildTime")
                        .value("2026-07-17T12:00:00Z"));
    }

    @Test
    void registrationStoresArgon2idHashProvisionsTenantAndMeExposesOnlyPublicFields()
            throws Exception {
        CsrfSession anonymous = csrf(null);

        MvcResult registration = register(anonymous, "  User@Example.COM  ",
                VALID_PASSWORD, "  Example User  ")
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/auth/me"))
                .andExpect(jsonPath("$.email").value("user@example.com"))
                .andExpect(jsonPath("$.displayName").value("Example User"))
                .andReturn();

        String storedHash = jdbcTemplate.queryForObject(
                "SELECT password_hash FROM identity_user WHERE email = ?",
                String.class, "user@example.com");
        assertNotNull(storedHash);
        assertTrue(storedHash.startsWith("$argon2id$"));
        assertNotEquals(VALID_PASSWORD, storedHash);

        UUID userId = UUID.fromString(JsonPath.read(
                registration.getResponse().getContentAsString(), "$.id"));
        UUID tenantId = jdbcTemplate.queryForObject("""
                SELECT tenant_id
                FROM tenant_user
                WHERE user_id = ?
                """, UUID.class, userId);
        assertNotNull(tenantId);
        assertNotEquals(userId, tenantId);
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT count(*) FROM tenant_user WHERE user_id = ?",
                Integer.class, userId));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT count(*) FROM tenant",
                Integer.class));

        Cookie authenticated = requiredCookie(registration);
        mockMvc.perform(get("/api/v1/auth/me").cookie(authenticated))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.*", hasSize(4)))
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.email").value("user@example.com"))
                .andExpect(jsonPath("$.displayName").value("Example User"))
                .andExpect(jsonPath("$.memberships").isArray())
                .andExpect(jsonPath("$.memberships", hasSize(0)))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.sessionId").doesNotExist());
    }

    @Test
    void normalizedDuplicateEmailIsRejectedByTheDatabaseInvariant() throws Exception {
        register(csrf(null), "ABC@x.com", VALID_PASSWORD, "First")
                .andExpect(status().isCreated());

        register(csrf(null), "abc@x.com", VALID_PASSWORD, "Second")
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("AUTH_EMAIL_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.correlationId").isNotEmpty());

        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT count(*) FROM identity_user WHERE email = 'abc@x.com'",
                Integer.class));
    }

    @Test
    void successfulLoginRegeneratesTheAnonymousJdbcSessionId() throws Exception {
        register(csrf(null), "login@example.com", VALID_PASSWORD, "Login User")
                .andExpect(status().isCreated());
        jdbcTemplate.update("DELETE FROM spring_session_attributes");
        jdbcTemplate.update("DELETE FROM spring_session");

        CsrfSession anonymous = csrf(null);
        String oldSessionId = anonymous.cookie().getValue();
        MvcResult login = login(anonymous, " LOGIN@example.com ", VALID_PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("login@example.com"))
                .andReturn();
        String newSessionId = requiredCookie(login).getValue();

        assertNotEquals(oldSessionId, newSessionId);
        assertEquals(0, sessionCount(oldSessionId));
        assertEquals(1, sessionCount(newSessionId));
    }

    @Test
    void logoutInvalidatesServerStateAndTheOldCookieCannotAccessMe() throws Exception {
        MvcResult registration = register(csrf(null), "logout@example.com",
                VALID_PASSWORD, "Logout User")
                .andExpect(status().isCreated())
                .andReturn();
        Cookie authenticated = requiredCookie(registration);
        CsrfSession current = csrf(authenticated);

        MvcResult logout = mockMvc.perform(post("/api/v1/auth/logout")
                        .cookie(authenticated)
                        .header(current.headerName(), current.token()))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""))
                .andReturn();

        Cookie cleared = logout.getResponse().getCookie(COOKIE_NAME);
        assertNotNull(cleared);
        assertEquals(0, cleared.getMaxAge());
        assertEquals(0, sessionCount(authenticated.getValue()));

        mockMvc.perform(get("/api/v1/auth/me").cookie(authenticated))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("AUTH_SESSION_EXPIRED"))
                .andExpect(jsonPath("$.correlationId").isNotEmpty());
    }

    @Test
    void csrfIsRequiredAndLocalCookieKeepsAllNonTlsProtections() throws Exception {
        MvcResult csrf = mockMvc.perform(get("/api/v1/security/csrf"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.headerName").value("X-CSRF-TOKEN"))
                .andReturn();
        String setCookie = csrf.getResponse().getHeader("Set-Cookie");
        assertNotNull(setCookie);
        assertTrue(setCookie.startsWith(COOKIE_NAME + "="));
        assertTrue(setCookie.contains("Path=/"));
        assertTrue(setCookie.contains("HttpOnly"));
        assertTrue(setCookie.contains("SameSite=Lax"));
        assertFalse(setCookie.contains("; Secure"));
        assertFalse(setCookie.contains("Domain="));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("csrf@example.com", VALID_PASSWORD, "CSRF")))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("CSRF_TOKEN_INVALID"))
                .andExpect(jsonPath("$.correlationId").isNotEmpty())
                .andExpect(result -> UUID.fromString(JsonPath.read(
                        result.getResponse().getContentAsString(), "$.correlationId")));
    }

    @Test
    void validationAndLoginFailuresUseStableNonEnumeratingProblemDetails() throws Exception {
        CsrfSession weakCsrf = csrf(null);
        register(weakCsrf, "weak@example.com", "passwordpassword", "Weak")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("password"))
                .andExpect(jsonPath("$.errors[0].code").value("PASSWORD_TOO_COMMON"));

        CsrfSession malformedCsrf = csrf(null);
        mockMvc.perform(post("/api/v1/auth/register")
                        .cookie(malformedCsrf.cookie())
                        .header(malformedCsrf.headerName(), malformedCsrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));

        CsrfSession unknownFieldCsrf = csrf(null);
        mockMvc.perform(post("/api/v1/auth/login")
                        .cookie(unknownFieldCsrf.cookie())
                        .header(unknownFieldCsrf.headerName(), unknownFieldCsrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"known@example.com","password":"value","extra":true}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));

        register(csrf(null), "known@example.com", VALID_PASSWORD, "Known")
                .andExpect(status().isCreated());
        CsrfSession loginCsrf = csrf(null);
        MvcResult wrongPassword = login(loginCsrf, "known@example.com",
                "wrong but long enough")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_INVALID_CREDENTIALS"))
                .andReturn();
        MvcResult unknownAccount = login(loginCsrf, "unknown@example.com",
                "wrong but long enough")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_INVALID_CREDENTIALS"))
                .andReturn();
        jdbcTemplate.update("UPDATE identity_user SET enabled = false WHERE email = ?",
                "known@example.com");
        MvcResult disabledAccount = login(loginCsrf, "known@example.com", VALID_PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_INVALID_CREDENTIALS"))
                .andReturn();

        String wrongPasswordDetail = JsonPath.read(
                wrongPassword.getResponse().getContentAsString(), "$.detail");
        String unknownAccountDetail = JsonPath.read(
                unknownAccount.getResponse().getContentAsString(), "$.detail");
        String disabledAccountDetail = JsonPath.read(
                disabledAccount.getResponse().getContentAsString(), "$.detail");
        assertEquals(wrongPasswordDetail, unknownAccountDetail);
        assertEquals(wrongPasswordDetail, disabledAccountDetail);
    }

    private org.springframework.test.web.servlet.ResultActions register(
            CsrfSession session, String email, String password, String displayName)
            throws Exception {
        return mockMvc.perform(post("/api/v1/auth/register")
                .cookie(session.cookie())
                .header(session.headerName(), session.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerJson(email, password, displayName)));
    }

    private org.springframework.test.web.servlet.ResultActions login(
            CsrfSession session, String email, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                .cookie(session.cookie())
                .header(session.headerName(), session.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"%s","password":"%s"}
                        """.formatted(email, password)));
    }

    private CsrfSession csrf(Cookie cookie) throws Exception {
        var request = get("/api/v1/security/csrf");
        if (cookie != null) {
            request.cookie(cookie);
        }
        MvcResult result = mockMvc.perform(request)
                .andExpect(status().isOk())
                .andReturn();
        Cookie resolvedCookie = cookie == null ? requiredCookie(result) : cookie;
        String body = result.getResponse().getContentAsString();
        return new CsrfSession(resolvedCookie,
                JsonPath.read(body, "$.token"),
                JsonPath.read(body, "$.headerName"));
    }

    private Cookie requiredCookie(MvcResult result) {
        Cookie cookie = result.getResponse().getCookie(COOKIE_NAME);
        assertNotNull(cookie);
        return cookie;
    }

    private int sessionCount(String sessionId) {
        String decodedSessionId = new String(Base64.getDecoder().decode(sessionId),
                StandardCharsets.UTF_8);
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM spring_session WHERE session_id = ?",
                Integer.class, decodedSessionId);
    }

    private String registerJson(String email, String password, String displayName) {
        return """
                {"email":"%s","password":"%s","displayName":"%s"}
                """.formatted(email, password, displayName);
    }

    private record CsrfSession(Cookie cookie, String token, String headerName) {
    }
}
