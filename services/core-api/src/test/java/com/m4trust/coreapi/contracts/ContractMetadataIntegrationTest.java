package com.m4trust.coreapi.contracts;

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.m4trust.coreapi.CoreApiApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(classes = CoreApiApplication.class, properties = {
        "app.environment=test",
        "app.version=test-release",
        "app.git-commit-sha=0123456789abcdef",
        "app.build-time=2026-07-17T12:00:00Z",
        "m4trust.release.git-commit-sha=0123456789abcdef0123456789abcdef01234567",
        "m4trust.contracts.probe-token=active-probe-token-value-32b!!!!!",
        "m4trust.contracts.probe-token-previous=previous-probe-token-value-32b!"
})
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Testcontainers
class ContractMetadataIntegrationTest {

    private static final String ACTIVE = "active-probe-token-value-32b!!!!!";
    private static final String PREVIOUS = "previous-probe-token-value-32b!";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17.5-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Test
    void missingTokenReturns401() throws Exception {
        mockMvc.perform(get("/internal/v1/contracts"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void wrongTokenReturns401() throws Exception {
        mockMvc.perform(get("/internal/v1/contracts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer wrong-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void activeTokenReturnsBundleProjection() throws Exception {
        MvcResult result = mockMvc.perform(get("/internal/v1/contracts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACTIVE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.service").value("core"))
                .andExpect(jsonPath("$.releaseRevision")
                        .value("0123456789abcdef0123456789abcdef01234567"))
                .andExpect(jsonPath("$.contractBundleDigest",
                        matchesPattern("^sha256:[a-f0-9]{64}$")))
                .andExpect(jsonPath("$.files").isArray())
                .andExpect(jsonPath("$.files", hasSize(greaterThan(0))))
                .andExpect(jsonPath("$.files[0].path").isString())
                .andExpect(jsonPath("$.files[0].sha256", matchesPattern("^[a-f0-9]{64}$")))
                .andReturn();
        String body = result.getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertFalse(body.contains(ACTIVE));
        org.junit.jupiter.api.Assertions.assertFalse(body.contains(PREVIOUS));
    }

    @Test
    void previousTokenAcceptedDuringRotation() throws Exception {
        mockMvc.perform(get("/internal/v1/contracts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + PREVIOUS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.service").value("core"));
    }

    @Test
    void noPublicApiRouteExposesContractBundle() throws Exception {
        mockMvc.perform(get("/api/v1/contracts"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/internal/v1/contracts"))
                .andExpect(status().isUnauthorized());
    }
}
