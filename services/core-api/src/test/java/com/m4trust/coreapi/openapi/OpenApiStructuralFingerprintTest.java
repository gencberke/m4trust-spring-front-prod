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

        assertFalse(diffs.isEmpty(), "negative fixture must detect injected path drift");
        assertTrue(diffs.stream().anyMatch(diff -> diff.contains("/__drift_probe__/fake")),
                () -> "expected fake-path drift, got: " + diffs);
    }

    @Test
    void pathTemplatesNormalizePositionally() throws Exception {
        Map<String, Object> committed = OpenApiYamlDocuments.loadCoreApiV1();
        var keys = OpenApiStructuralFingerprint.fromDocument(committed).keySet();
        // Fingerprint compares path templates positionally so design/runtime
        // parameter names may differ without creating a false drift signal.
        assertTrue(keys.stream().anyMatch(key -> key.contains("/deals/{}/")),
                () -> "expected positional {} path templates, got: " + keys);
        assertTrue(keys.stream().noneMatch(key -> key.contains("{dealId}")),
                () -> "path parameter names must be positional {}, got: " + keys);

        // Loader must still restore real braces before fingerprinting; otherwise
        // SnakeYAML would drop templates entirely and leave bare /deals/ segments.
        Map<String, Object> rawLoaded = OpenApiYamlDocuments.loadCoreApiV1();
        Object paths = rawLoaded.get("paths");
        assertTrue(paths instanceof Map<?, ?> pathMap
                        && pathMap.keySet().stream().map(String::valueOf)
                        .anyMatch(path -> path.contains("{dealId}")),
                "YAML loader must preserve {dealId} before positional normalize");
    }
}
