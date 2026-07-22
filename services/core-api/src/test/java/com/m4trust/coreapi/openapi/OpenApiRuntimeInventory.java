package com.m4trust.coreapi.openapi;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ADR-021 raw runtime inventory: exact public path/method keys plus reliably
 * emitted named path servlet parameters (name/in/required).
 * <p>
 * Query/header/cookie design parameters are not compared here: springdoc reflects
 * custom {@code OperationContext} resolvers as spurious query parameters, and
 * design headers (LegalEntityContext, Idempotency-Key) are covered by committed
 * OpenAPI plus focused MockMvc HTTP tests.
 */
final class OpenApiRuntimeInventory {

    private static final Set<String> HTTP_METHODS = Set.of(
            "get", "put", "post", "delete", "options", "head", "patch", "trace");
    private static final Pattern PATH_PARAM = Pattern.compile("\\{([A-Za-z][A-Za-z0-9_]*)\\}");

    private OpenApiRuntimeInventory() {
    }

    record Parameter(String name, String in, boolean required) implements Comparable<Parameter> {
        @Override
        public int compareTo(Parameter other) {
            return Comparator.comparing(Parameter::name)
                    .thenComparing(Parameter::in)
                    .thenComparing(Parameter::required)
                    .compare(this, other);
        }
    }

    record Operation(Set<Parameter> parameters) {
    }

    static Map<String, Operation> fromDocument(Map<String, Object> document) {
        String base = serverBasePath(document);
        Map<String, Operation> operations = new TreeMap<>();
        Object pathsNode = document.get("paths");
        if (!(pathsNode instanceof Map<?, ?> paths)) {
            return operations;
        }
        for (Map.Entry<?, ?> pathEntry : paths.entrySet()) {
            if (!(pathEntry.getValue() instanceof Map<?, ?> pathItem)) {
                continue;
            }
            String normalizedPath = normalizePath(String.valueOf(pathEntry.getKey()), base);
            if (shouldIgnorePath(normalizedPath)) {
                continue;
            }
            for (String method : HTTP_METHODS) {
                Object operationNode = pathItem.get(method);
                if (!(operationNode instanceof Map<?, ?> operation)) {
                    continue;
                }
                operations.put(
                        method.toUpperCase(Locale.ROOT) + " " + normalizedPath,
                        new Operation(inventoryParameters(document, normalizedPath, operation)));
            }
        }
        return operations;
    }

    static List<String> diff(Map<String, Operation> expected, Map<String, Operation> actual) {
        List<String> diffs = new ArrayList<>();
        for (String key : sorted(expected.keySet())) {
            if (!actual.containsKey(key)) {
                diffs.add("missing operation: " + key);
            }
        }
        for (String key : sorted(actual.keySet())) {
            if (!expected.containsKey(key)) {
                diffs.add("unexpected operation: " + key);
            }
        }
        for (String key : sorted(expected.keySet())) {
            if (!actual.containsKey(key)) {
                continue;
            }
            Set<Parameter> left = expected.get(key).parameters();
            Set<Parameter> right = actual.get(key).parameters();
            if (!left.equals(right)) {
                diffs.add("parameter drift " + key + ": expected=" + left + " actual=" + right);
            }
        }
        return diffs;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> withInjectedFakeRoute(Map<String, Object> document) {
        Map<String, Object> mutated = deepCopyMap(document);
        Map<String, Object> paths = (Map<String, Object>) mutated.computeIfAbsent(
                "paths", ignored -> new LinkedHashMap<>());
        Map<String, Object> fakeOperation = new LinkedHashMap<>();
        fakeOperation.put("operationId", "inventoryDriftProbe");
        fakeOperation.put("responses", Map.of("200", Map.of("description", "probe")));
        paths.put("/__inventory_probe__/fake", Map.of("get", fakeOperation));
        return mutated;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> withRenamedPathParameter(Map<String, Object> document) {
        Map<String, Object> mutated = deepCopyMap(document);
        Map<String, Object> paths = (Map<String, Object>) mutated.get("paths");
        if (paths == null || paths.isEmpty()) {
            throw new IllegalStateException("document has no paths");
        }
        String targetKey = null;
        for (String key : paths.keySet()) {
            if (key.contains("{") && key.contains("}")) {
                targetKey = key;
                break;
            }
        }
        if (targetKey == null) {
            throw new IllegalStateException("document has no path-parameter template");
        }
        Object item = paths.remove(targetKey);
        String renamed = PATH_PARAM.matcher(targetKey).replaceFirst("{__driftPathParam__}");
        paths.put(renamed, item);
        return mutated;
    }

    private static Set<Parameter> inventoryParameters(
            Map<String, Object> document, String path, Map<?, ?> operation) {
        Set<Parameter> parameters = new TreeSet<>();
        Matcher matcher = PATH_PARAM.matcher(path);
        while (matcher.find()) {
            parameters.add(new Parameter(matcher.group(1), "path", true));
        }
        // Prefer template-derived path names; explicit path params must not disagree.
        Object parametersNode = operation.get("parameters");
        if (!(parametersNode instanceof List<?> parameterList)) {
            return parameters;
        }
        for (Object parameterNode : parameterList) {
            if (!(parameterNode instanceof Map<?, ?> parameter)) {
                continue;
            }
            Object ref = parameter.get("$ref");
            if (ref instanceof String refValue) {
                Map<?, ?> resolved = resolveParameterRef(document, refValue);
                if (resolved == null) {
                    continue;
                }
                parameter = resolved;
            }
            Object name = parameter.get("name");
            Object in = parameter.get("in");
            if (!(name instanceof String nameValue) || !(in instanceof String inValue)) {
                continue;
            }
            if (!"path".equals(inValue)) {
                continue;
            }
            boolean required = parameter.containsKey("required")
                    ? Boolean.TRUE.equals(parameter.get("required"))
                    : true;
            parameters.add(new Parameter(nameValue, "path", required));
        }
        return parameters;
    }

    private static Map<?, ?> resolveParameterRef(Map<String, Object> document, String ref) {
        if (!ref.startsWith("#/components/parameters/")) {
            return null;
        }
        Object componentsNode = document.get("components");
        if (!(componentsNode instanceof Map<?, ?> components)) {
            return null;
        }
        Object parametersNode = components.get("parameters");
        if (!(parametersNode instanceof Map<?, ?> parameters)) {
            return null;
        }
        Object resolved = parameters.get(ref.substring("#/components/parameters/".length()));
        return resolved instanceof Map<?, ?> map ? map : null;
    }

    private static boolean shouldIgnorePath(String path) {
        return path.startsWith("/actuator")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/error")
                || path.startsWith("/internal");
    }

    private static String serverBasePath(Map<String, Object> document) {
        Object serversNode = document.get("servers");
        if (!(serversNode instanceof List<?> servers) || servers.isEmpty()) {
            return "";
        }
        Object first = servers.getFirst();
        if (!(first instanceof Map<?, ?> server)) {
            return "";
        }
        Object urlNode = server.get("url");
        if (!(urlNode instanceof String url) || url.isBlank()) {
            return "";
        }
        String trimmed = url.replaceAll("/+$", "");
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            int scheme = trimmed.indexOf("://");
            int pathStart = trimmed.indexOf('/', scheme + 3);
            return pathStart < 0 ? "" : trimmed.substring(pathStart).replaceAll("/+$", "");
        }
        return trimmed;
    }

    private static String normalizePath(String path, String base) {
        String normalized = path.startsWith("/") ? path : "/" + path;
        if (!base.isEmpty() && normalized.startsWith(base + "/")) {
            normalized = normalized.substring(base.length());
        }
        else if (!base.isEmpty() && normalized.equals(base)) {
            normalized = "/";
        }
        else if (normalized.startsWith("/api/v1/")) {
            normalized = normalized.substring("/api/v1".length());
        }
        else if (normalized.equals("/api/v1")) {
            normalized = "/";
        }
        return normalized;
    }

    private static List<String> sorted(Collection<String> values) {
        return values.stream().sorted().toList();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> deepCopyMap(Map<String, Object> source) {
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Map<?, ?> nested) {
                copy.put(entry.getKey(), deepCopyMap((Map<String, Object>) nested));
            }
            else if (value instanceof List<?> list) {
                copy.put(entry.getKey(), deepCopyList(list));
            }
            else {
                copy.put(entry.getKey(), value);
            }
        }
        return copy;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> deepCopyList(List<?> source) {
        List<Object> copy = new ArrayList<>(source.size());
        for (Object value : source) {
            if (value instanceof Map<?, ?> nested) {
                copy.add(deepCopyMap((Map<String, Object>) nested));
            }
            else if (value instanceof List<?> list) {
                copy.add(deepCopyList(list));
            }
            else {
                copy.add(value);
            }
        }
        return copy;
    }
}
