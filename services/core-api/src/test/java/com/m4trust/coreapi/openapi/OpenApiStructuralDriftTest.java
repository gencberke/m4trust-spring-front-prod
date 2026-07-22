package com.m4trust.coreapi.openapi;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.m4trust.coreapi.CoreApiApplication;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
        Map<String, Object> runtime = projectLiveRuntime(committed);

        List<String> diffs = OpenApiStructuralFingerprint.diff(
                OpenApiStructuralFingerprint.fromDocument(committed),
                OpenApiStructuralFingerprint.fromDocument(runtime));

        assertTrue(diffs.isEmpty(), () -> "OpenAPI structural drift:\n" + String.join("\n", diffs));
    }

    @ParameterizedTest(name = "live negative {0}")
    @CsvSource({
            "parameter, parameter drift",
            "security, security drift",
            "status, status drift",
            "media-type, media-type drift",
            "schema-ref, schema $ref drift"
    })
    void liveRuntimeNegativeMatrixFailsIndependentDimensions(
            String dimension, String expectedFragment) throws Exception {
        Map<String, Object> committed = OpenApiYamlDocuments.loadCoreApiV1();
        Map<String, Object> runtime = projectLiveRuntime(committed);
        Function<Map<String, Object>, Map<String, Object>> mutator = switch (dimension) {
            case "parameter" -> OpenApiStructuralFingerprint::withMutatedParameter;
            case "security" -> OpenApiStructuralFingerprint::withMutatedSecurity;
            case "status" -> OpenApiStructuralFingerprint::withMutatedStatus;
            case "media-type" -> OpenApiStructuralFingerprint::withMutatedMediaType;
            case "schema-ref" -> OpenApiStructuralFingerprint::withMutatedSchemaRef;
            default -> throw new IllegalArgumentException(dimension);
        };
        Map<String, Object> mutated = mutator.apply(runtime);
        List<String> diffs = OpenApiStructuralFingerprint.diff(
                OpenApiStructuralFingerprint.fromDocument(runtime),
                OpenApiStructuralFingerprint.fromDocument(mutated));
        assertFalse(diffs.isEmpty(), "live negative matrix must detect " + dimension);
        assertTrue(diffs.stream().anyMatch(diff -> diff.contains(expectedFragment)),
                () -> "expected '" + expectedFragment + "', got: " + diffs);
    }

    private Map<String, Object> projectLiveRuntime(Map<String, Object> committed) throws Exception {
        return ContractOpenApiProjection.projectCommittedCatalogs(committed, loadRuntimeOpenApi());
    }

    private Map<String, Object> loadRuntimeOpenApi() throws Exception {
        MvcResult result = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn();
        return OBJECT_MAPPER.readValue(result.getResponse().getContentAsByteArray(), MAP_TYPE);
    }
}
