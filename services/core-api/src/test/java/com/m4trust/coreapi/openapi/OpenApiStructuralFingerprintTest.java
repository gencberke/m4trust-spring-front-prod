package com.m4trust.coreapi.openapi;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class OpenApiStructuralFingerprintTest {

    @Test
    void deliberatePathInjectionFailsStructuralComparison() throws Exception {
        Map<String, Object> committed = OpenApiYamlDocuments.loadCoreApiV1();
        Map<String, Object> mutated = OpenApiStructuralFingerprint.withInjectedFakePath(committed);
        List<String> diffs = OpenApiStructuralFingerprint.diff(
                OpenApiStructuralFingerprint.fromDocument(committed),
                OpenApiStructuralFingerprint.fromDocument(mutated));
        assertFalse(diffs.isEmpty(), "committed semantic negative must detect injected path");
        assertTrue(diffs.stream().anyMatch(diff -> diff.contains("/__drift_probe__/fake")),
                () -> "expected fake-path drift, got: " + diffs);
    }

    @Test
    void pathTemplatesKeepNamedParameters() throws Exception {
        Map<String, Object> committed = OpenApiYamlDocuments.loadCoreApiV1();
        var keys = OpenApiRuntimeInventory.fromDocument(committed).keySet();
        assertTrue(keys.stream().anyMatch(key -> key.contains("/deals/{dealId}/")),
                () -> "expected named {dealId} path templates, got: " + keys);
        assertTrue(keys.stream().noneMatch(key -> key.contains("/deals/{}/")),
                () -> "positional {} path templates must not be used, got: " + keys);
        assertTrue(keys.stream().anyMatch(key -> key.contains("{ruleSetVersionId}")),
                () -> "expected named {ruleSetVersionId}, got: " + keys);
    }
}
