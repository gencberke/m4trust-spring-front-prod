package com.m4trust.coreapi.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

class ErrorCatalogExactSetTest {

    private static final Set<String> REMOVED_COMBINED = Set.of(
            "ACCESS_DENIED",
            "DEAL_OR_LEGAL_ENTITY_NOT_FOUND_OR_HIDDEN",
            "FULFILLMENT_OR_EVIDENCE_NOT_FOUND_OR_HIDDEN");

    @Test
    void javaApiErrorCodeMatchesOpenApiExactSet() throws IOException {
        Set<String> openApi = readOpenApiEnum("ApiErrorCode");
        Set<String> java = Stream.of(ApiErrorCode.values())
                .map(Enum::name)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        assertEquals(openApi, java);
        assertFalse(java.stream().anyMatch(REMOVED_COMBINED::contains));
        assertFalse(openApi.stream().anyMatch(REMOVED_COMBINED::contains));
    }

    @Test
    void javaFieldErrorCodeMatchesOpenApiExactSet() throws IOException {
        Set<String> openApi = readOpenApiEnum("FieldErrorCode");
        Set<String> java = Stream.of(FieldErrorCode.values())
                .map(Enum::name)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        assertEquals(openApi, java);
    }

    private static Set<String> readOpenApiEnum(String schemaName) throws IOException {
        Path openApi = resolveOpenApi();
        java.util.List<String> lines = Files.readAllLines(openApi);
        Set<String> values = new LinkedHashSet<>();
        boolean inSchema = false;
        boolean inEnum = false;
        for (String line : lines) {
            String trimmed = line.stripTrailing();
            if (!inSchema) {
                if (trimmed.equals("    " + schemaName + ":")) {
                    inSchema = true;
                }
                continue;
            }
            if (trimmed.startsWith("    ") && !trimmed.startsWith("     ")
                    && !trimmed.equals("    " + schemaName + ":")) {
                break;
            }
            if (trimmed.equals("      enum:")) {
                inEnum = true;
                continue;
            }
            if (inEnum) {
                String item = trimmed.strip();
                if (item.startsWith("- ")) {
                    values.add(item.substring(2).trim());
                    continue;
                }
                if (!item.isEmpty() && !item.startsWith("#")) {
                    break;
                }
            }
        }
        if (values.isEmpty()) {
            throw new IllegalStateException("OpenAPI enum not found: " + schemaName);
        }
        return values;
    }

    private static Path resolveOpenApi() {
        Path cwd = Path.of("").toAbsolutePath();
        Path candidate = cwd.resolve("contracts/openapi/core-api-v1.yaml");
        if (Files.isRegularFile(candidate)) {
            return candidate;
        }
        candidate = cwd.resolve("../../contracts/openapi/core-api-v1.yaml");
        if (Files.isRegularFile(candidate)) {
            return candidate.normalize();
        }
        throw new IllegalStateException("Unable to locate contracts/openapi/core-api-v1.yaml from "
                + cwd.toString().toLowerCase(Locale.ROOT));
    }
}
