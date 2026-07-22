package com.m4trust.coreapi.openapi;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.m4trust.coreapi.CoreApiApplication;
import com.m4trust.coreapi.openapi.OpenApiStructuralFingerprint.OperationFingerprint;
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

@SpringBootTest(classes = CoreApiApplication.class, properties = {
        "springdoc.api-docs.enabled=true",
        "springdoc.swagger-ui.enabled=false",
        "springdoc.paths-to-match=/api/v1/**",
        "springdoc.show-actuator=false",
        // Avoid broker/storage side effects while generating the servlet map.
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
    void runtimeOpenApiMatchesCommittedPublicContractStructure() throws Exception {
        Map<String, Object> committed = OpenApiYamlDocuments.loadCoreApiV1();
        Map<String, Object> runtime = loadRuntimeOpenApi();

        List<String> diffs = diffRuntimeToCommitted(
                OpenApiStructuralFingerprint.fromDocument(committed),
                OpenApiStructuralFingerprint.fromDocument(runtime));

        assertTrue(diffs.isEmpty(), () -> "OpenAPI structural drift:\n" + String.join("\n", diffs));
    }

    /**
     * Runtime springdoc reflects servlet mappings without OpenAPI annotations.
     * Enforce exact public operation identity and path-template parameters.
     * Richer committed fields (error catalogs, design schema names, CSRF schemes)
     * are covered by the exact comparator negative fixture and contract validator.
     */
    static List<String> diffRuntimeToCommitted(
            Map<String, OperationFingerprint> committed,
            Map<String, OperationFingerprint> runtime) {
        List<String> diffs = new ArrayList<>();
        Set<String> committedKeys = committed.keySet();
        Set<String> runtimeKeys = runtime.keySet();
        for (String key : committedKeys.stream().sorted().toList()) {
            if (!runtimeKeys.contains(key)) {
                diffs.add("missing operation: " + key);
            }
        }
        for (String key : runtimeKeys.stream().sorted().toList()) {
            if (!committedKeys.contains(key)) {
                diffs.add("unexpected operation: " + key);
            }
        }
        return diffs;
    }

    private Map<String, Object> loadRuntimeOpenApi() throws Exception {
        MvcResult result = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn();
        return OBJECT_MAPPER.readValue(result.getResponse().getContentAsByteArray(), MAP_TYPE);
    }
}
