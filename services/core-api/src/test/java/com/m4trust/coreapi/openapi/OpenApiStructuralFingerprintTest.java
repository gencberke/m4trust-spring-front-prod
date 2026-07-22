package com.m4trust.coreapi.openapi;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class OpenApiStructuralFingerprintTest {

    @Test
    void deliberatePathInjectionFailsStructuralComparison() throws Exception {
        assertDimensionDrift(
                OpenApiStructuralFingerprint::withInjectedFakePath,
                "/__drift_probe__/fake");
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
            "parameter, parameter drift",
            "security, security drift",
            "status, status drift",
            "media-type, media-type drift",
            "schema-ref, schema $ref drift"
    })
    void negativeMatrixFailsIndependentDimensions(String dimension, String expectedFragment)
            throws Exception {
        Function<Map<String, Object>, Map<String, Object>> mutator = switch (dimension) {
            case "parameter" -> OpenApiStructuralFingerprint::withMutatedParameter;
            case "security" -> OpenApiStructuralFingerprint::withMutatedSecurity;
            case "status" -> OpenApiStructuralFingerprint::withMutatedStatus;
            case "media-type" -> OpenApiStructuralFingerprint::withMutatedMediaType;
            case "schema-ref" -> OpenApiStructuralFingerprint::withMutatedSchemaRef;
            default -> throw new IllegalArgumentException(dimension);
        };
        assertDimensionDrift(mutator, expectedFragment);
    }

    @Test
    void pathTemplatesKeepNamedParameters() throws Exception {
        Map<String, Object> committed = OpenApiYamlDocuments.loadCoreApiV1();
        var keys = OpenApiStructuralFingerprint.fromDocument(committed).keySet();
        assertTrue(keys.stream().anyMatch(key -> key.contains("/deals/{dealId}/")),
                () -> "expected named {dealId} path templates, got: " + keys);
        assertTrue(keys.stream().noneMatch(key -> key.contains("/deals/{}/")),
                () -> "positional {} path templates must not be used, got: " + keys);
        assertTrue(keys.stream().anyMatch(key -> key.contains("{ruleSetVersionId}")),
                () -> "expected named {ruleSetVersionId}, got: " + keys);
    }

    private static void assertDimensionDrift(
            Function<Map<String, Object>, Map<String, Object>> mutator,
            String expectedFragment) throws Exception {
        Map<String, Object> committed = OpenApiYamlDocuments.loadCoreApiV1();
        Map<String, Object> mutated = mutator.apply(committed);
        List<String> diffs = OpenApiStructuralFingerprint.diff(
                OpenApiStructuralFingerprint.fromDocument(committed),
                OpenApiStructuralFingerprint.fromDocument(mutated));
        assertFalse(diffs.isEmpty(), "negative fixture must detect drift for " + expectedFragment);
        assertTrue(diffs.stream().anyMatch(diff -> diff.contains(expectedFragment)),
                () -> "expected fragment '" + expectedFragment + "', got: " + diffs);
    }
}
