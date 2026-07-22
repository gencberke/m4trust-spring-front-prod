package com.m4trust.coreapi.openapi;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.m4trust.coreapi.CoreApiApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * ADR-021 live inventory gate: raw {@code /v3/api-docs} vs committed OpenAPI for
 * exact public paths/methods and named path servlet parameters only.
 */
@SpringBootTest(classes = CoreApiApplication.class, properties = {
        "springdoc.api-docs.enabled=true",
        "springdoc.swagger-ui.enabled=false",
        "springdoc.paths-to-match=/api/v1/**",
        "springdoc.show-actuator=false",
        "app.messaging.topology.enabled=false",
        "app.messaging.relay.enabled=false"
})
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles({"local", "contract"})
@Testcontainers
class OpenApiStructuralDriftTest {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17.5-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Test
    void rawRuntimeInventoryMatchesCommittedPublicRoutesAndServletParameters() throws Exception {
        Map<String, Object> committed = OpenApiYamlDocuments.loadCoreApiV1();
        Map<String, Object> runtime = loadRuntimeOpenApi();

        List<String> diffs = OpenApiRuntimeInventory.diff(
                OpenApiRuntimeInventory.fromDocument(committed),
                OpenApiRuntimeInventory.fromDocument(runtime));

        assertTrue(diffs.isEmpty(), () -> "OpenAPI runtime inventory drift:\n" + String.join("\n", diffs));
    }

    @Test
    void runtimeSideRouteDriftFailsInventoryGate() throws Exception {
        Map<String, Object> committed = OpenApiYamlDocuments.loadCoreApiV1();
        Map<String, Object> runtime = loadRuntimeOpenApi();
        Map<String, Object> mutatedRuntime = OpenApiRuntimeInventory.withInjectedFakeRoute(runtime);

        List<String> diffs = OpenApiRuntimeInventory.diff(
                OpenApiRuntimeInventory.fromDocument(committed),
                OpenApiRuntimeInventory.fromDocument(mutatedRuntime));

        assertFalse(diffs.isEmpty(), "runtime-side route drift must fail the inventory gate");
        assertTrue(diffs.stream().anyMatch(diff -> diff.contains("/__inventory_probe__/fake")),
                () -> "expected injected route drift, got: " + diffs);
    }

    @Test
    void runtimeSideNamedPathParameterDriftFailsInventoryGate() throws Exception {
        Map<String, Object> committed = OpenApiYamlDocuments.loadCoreApiV1();
        Map<String, Object> runtime = loadRuntimeOpenApi();
        Map<String, Object> mutatedRuntime = OpenApiRuntimeInventory.withRenamedPathParameter(runtime);

        List<String> diffs = OpenApiRuntimeInventory.diff(
                OpenApiRuntimeInventory.fromDocument(committed),
                OpenApiRuntimeInventory.fromDocument(mutatedRuntime));

        assertFalse(diffs.isEmpty(),
                "runtime-side named path-parameter drift must fail the inventory gate");
        assertTrue(diffs.stream().anyMatch(diff ->
                        diff.contains("missing operation:")
                                || diff.contains("unexpected operation:")
                                || diff.contains("parameter drift")
                                || diff.contains("__driftPathParam__")),
                () -> "expected named servlet-parameter drift, got: " + diffs);
    }

    private Map<String, Object> loadRuntimeOpenApi() throws Exception {
        MvcResult result = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn();
        return OBJECT_MAPPER.readValue(result.getResponse().getContentAsByteArray(), MAP_TYPE);
    }
}
