package com.m4trust.coreapi.openapi;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Projects committed design security/response/requestBody catalogs onto a live
 * springdoc document while keeping servlet-sourced paths and parameters.
 */
final class ContractOpenApiProjection {

    private static final Set<String> HTTP_METHODS = Set.of(
            "get", "put", "post", "delete", "options", "head", "patch", "trace");

    private ContractOpenApiProjection() {
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> projectCommittedCatalogs(
            Map<String, Object> committed,
            Map<String, Object> live) {
        Map<String, Object> projected = deepCopy(live);
        Map<String, Object> committedPaths = (Map<String, Object>) committed.get("paths");
        Map<String, Object> livePaths = (Map<String, Object>) projected.get("paths");
        if (committedPaths == null || livePaths == null) {
            return projected;
        }
        Object committedComponents = committed.get("components");
        if (committedComponents instanceof Map<?, ?> components) {
            Map<String, Object> liveComponents = (Map<String, Object>) projected
                    .computeIfAbsent("components", ignored -> new LinkedHashMap<>());
            if (components.get("securitySchemes") != null) {
                liveComponents.put("securitySchemes",
                        deepCopyValue(components.get("securitySchemes")));
            }
            if (components.get("parameters") != null) {
                liveComponents.put("parameters",
                        deepCopyValue(components.get("parameters")));
            }
        }
        for (Map.Entry<String, Object> liveEntry : livePaths.entrySet()) {
            if (!(liveEntry.getValue() instanceof Map<?, ?> liveItemRaw)) {
                continue;
            }
            Map<String, Object> liveItem = (Map<String, Object>) liveItemRaw;
            String relative = stripApiPrefix(liveEntry.getKey());
            Object committedItemNode = committedPaths.get(relative);
            if (!(committedItemNode instanceof Map<?, ?> committedItemRaw)) {
                continue;
            }
            Map<String, Object> committedItem = (Map<String, Object>) committedItemRaw;
            for (String method : HTTP_METHODS) {
                Object liveOpNode = liveItem.get(method);
                Object committedOpNode = committedItem.get(method);
                if (!(liveOpNode instanceof Map<?, ?> liveOpRaw)
                        || !(committedOpNode instanceof Map<?, ?> committedOpRaw)) {
                    continue;
                }
                Map<String, Object> liveOp = (Map<String, Object>) liveOpRaw;
                Map<String, Object> committedOp = (Map<String, Object>) committedOpRaw;
                Object liveParameters = liveOp.get("parameters");
                if (committedOp.containsKey("security")) {
                    liveOp.put("security", deepCopyValue(committedOp.get("security")));
                }
                if (committedOp.containsKey("responses")) {
                    liveOp.put("responses", deepCopyValue(committedOp.get("responses")));
                }
                if (committedOp.containsKey("requestBody")) {
                    liveOp.put("requestBody", deepCopyValue(committedOp.get("requestBody")));
                }
                // Prefer committed parameter catalogs (headers/idempotency/$refs). Named
                // path templates remain in live path keys from springdoc reflection.
                if (committedOp.containsKey("parameters")) {
                    liveOp.put("parameters", deepCopyValue(committedOp.get("parameters")));
                }
                else {
                    liveOp.remove("parameters");
                }
            }
        }
        return projected;
    }

    private static String stripApiPrefix(String path) {
        String normalized = path.startsWith("/") ? path : "/" + path;
        if (normalized.startsWith("/api/v1/")) {
            return normalized.substring("/api/v1".length());
        }
        if (normalized.equals("/api/v1")) {
            return "/";
        }
        return normalized;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> deepCopy(Map<String, Object> source) {
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            copy.put(entry.getKey(), deepCopyValue(entry.getValue()));
        }
        return copy;
    }

    @SuppressWarnings("unchecked")
    private static Object deepCopyValue(Object value) {
        if (value instanceof Map<?, ?> nested) {
            return deepCopy((Map<String, Object>) nested);
        }
        if (value instanceof List<?> list) {
            return list.stream().map(ContractOpenApiProjection::deepCopyValue).toList();
        }
        return value;
    }
}
