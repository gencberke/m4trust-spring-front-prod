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

/**
 * Structural OpenAPI fingerprint used by runtime drift gates.
 * Descriptions and example ordering are ignored.
 */
final class OpenApiStructuralFingerprint {

    private static final Set<String> HTTP_METHODS = Set.of(
            "get", "put", "post", "delete", "options", "head", "patch", "trace");

    private OpenApiStructuralFingerprint() {
    }

    static Map<String, OperationFingerprint> fromDocument(Map<String, Object> document) {
        String base = serverBasePath(document);
        Map<String, OperationFingerprint> operations = new TreeMap<>();
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
                operations.put(method.toUpperCase(Locale.ROOT) + " " + normalizedPath,
                        OperationFingerprint.from(document, operation));
            }
        }
        return operations;
    }

    static List<String> diff(
            Map<String, OperationFingerprint> expected,
            Map<String, OperationFingerprint> actual) {
        List<String> diffs = new ArrayList<>();
        Set<String> expectedKeys = expected.keySet();
        Set<String> actualKeys = actual.keySet();
        for (String key : sorted(expectedKeys)) {
            if (!actualKeys.contains(key)) {
                diffs.add("missing operation: " + key);
            }
        }
        for (String key : sorted(actualKeys)) {
            if (!expectedKeys.contains(key)) {
                diffs.add("unexpected operation: " + key);
            }
        }
        for (String key : sorted(expectedKeys)) {
            if (!actualKeys.contains(key)) {
                continue;
            }
            OperationFingerprint left = expected.get(key);
            OperationFingerprint right = actual.get(key);
            if (!left.parameters().equals(right.parameters())) {
                diffs.add("parameter drift " + key + ": expected=" + left.parameters()
                        + " actual=" + right.parameters());
            }
            if (!left.security().equals(right.security())) {
                diffs.add("security drift " + key + ": expected=" + left.security()
                        + " actual=" + right.security());
            }
            if (!left.statusCodes().equals(right.statusCodes())) {
                diffs.add("status drift " + key + ": expected=" + left.statusCodes()
                        + " actual=" + right.statusCodes());
            }
            if (!left.mediaTypes().equals(right.mediaTypes())) {
                diffs.add("media-type drift " + key + ": expected=" + left.mediaTypes()
                        + " actual=" + right.mediaTypes());
            }
            if (!left.schemaRefs().equals(right.schemaRefs())) {
                diffs.add("schema $ref drift " + key + ": expected=" + left.schemaRefs()
                        + " actual=" + right.schemaRefs());
            }
        }
        return diffs;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> withInjectedFakePath(Map<String, Object> document) {
        Map<String, Object> mutated = deepCopyMap(document);
        Map<String, Object> paths = (Map<String, Object>) mutated.computeIfAbsent(
                "paths", ignored -> new LinkedHashMap<>());
        Map<String, Object> fakeOperation = new LinkedHashMap<>();
        fakeOperation.put("operationId", "driftProbeFake");
        Map<String, Object> responses = new LinkedHashMap<>();
        Map<String, Object> ok = new LinkedHashMap<>();
        ok.put("description", "probe");
        responses.put("200", ok);
        fakeOperation.put("responses", responses);
        Map<String, Object> fakePath = new LinkedHashMap<>();
        fakePath.put("get", fakeOperation);
        paths.put("/__drift_probe__/fake", fakePath);
        return mutated;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> withMutatedParameter(Map<String, Object> document) {
        Map<String, Object> mutated = deepCopyMap(document);
        Map<String, Object> operation = firstPublicOperation(mutated);
        List<Object> parameters = (List<Object>) operation.computeIfAbsent(
                "parameters", ignored -> new ArrayList<>());
        Map<String, Object> rogue = new LinkedHashMap<>();
        rogue.put("name", "__drift_param__");
        rogue.put("in", "query");
        rogue.put("required", true);
        parameters.add(rogue);
        return mutated;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> withMutatedSecurity(Map<String, Object> document) {
        Map<String, Object> mutated = deepCopyMap(document);
        Map<String, Object> operation = firstPublicOperation(mutated);
        Map<String, Object> requirement = new LinkedHashMap<>();
        requirement.put("__drift_scheme__", List.of());
        operation.put("security", List.of(requirement));
        return mutated;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> withMutatedStatus(Map<String, Object> document) {
        Map<String, Object> mutated = deepCopyMap(document);
        Map<String, Object> operation = firstPublicOperation(mutated);
        Map<String, Object> responses = (Map<String, Object>) operation.computeIfAbsent(
                "responses", ignored -> new LinkedHashMap<>());
        Map<String, Object> probe = new LinkedHashMap<>();
        probe.put("description", "drift status probe");
        responses.put("599", probe);
        return mutated;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> withMutatedMediaType(Map<String, Object> document) {
        Map<String, Object> mutated = deepCopyMap(document);
        Map<String, Object> operation = firstPublicOperation(mutated);
        Map<String, Object> responses = (Map<String, Object>) operation.get("responses");
        if (responses == null || responses.isEmpty()) {
            throw new IllegalStateException("document has no responses to mutate");
        }
        Map.Entry<String, Object> first = responses.entrySet().iterator().next();
        Map<String, Object> response = first.getValue() instanceof Map<?, ?> existing
                ? deepCopyMap((Map<String, Object>) existing)
                : new LinkedHashMap<>();
        Map<String, Object> content = (Map<String, Object>) response.computeIfAbsent(
                "content", ignored -> new LinkedHashMap<>());
        Map<String, Object> media = new LinkedHashMap<>();
        media.put("schema", Map.of("type", "string"));
        content.put("application/vnd.m4trust.drift+json", media);
        responses.put(first.getKey(), response);
        return mutated;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> withMutatedSchemaRef(Map<String, Object> document) {
        Map<String, Object> mutated = deepCopyMap(document);
        Map<String, Object> operation = firstPublicOperation(mutated);
        Map<String, Object> responses = (Map<String, Object>) operation.get("responses");
        if (responses == null || responses.isEmpty()) {
            throw new IllegalStateException("document has no responses to mutate");
        }
        Map.Entry<String, Object> first = responses.entrySet().iterator().next();
        Map<String, Object> response = first.getValue() instanceof Map<?, ?> existing
                ? deepCopyMap((Map<String, Object>) existing)
                : new LinkedHashMap<>();
        Map<String, Object> content = (Map<String, Object>) response.computeIfAbsent(
                "content", ignored -> new LinkedHashMap<>());
        Map<String, Object> media = new LinkedHashMap<>();
        media.put("schema", Map.of("$ref", "#/components/schemas/__DriftProbeSchema__"));
        content.put("application/json", media);
        responses.put(first.getKey(), response);
        return mutated;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> firstPublicOperation(Map<String, Object> document) {
        Map<String, Object> paths = (Map<String, Object>) document.get("paths");
        if (paths == null || paths.isEmpty()) {
            throw new IllegalStateException("document has no paths");
        }
        for (Object pathItemNode : paths.values()) {
            if (!(pathItemNode instanceof Map<?, ?> pathItem)) {
                continue;
            }
            for (String method : HTTP_METHODS) {
                Object operationNode = pathItem.get(method);
                if (operationNode instanceof Map<?, ?> operation) {
                    return (Map<String, Object>) operation;
                }
            }
        }
        throw new IllegalStateException("document has no operations");
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
            // Runtime springdoc emits absolute servlet paths including /api/v1.
            normalized = normalized.substring("/api/v1".length());
        }
        else if (normalized.equals("/api/v1")) {
            normalized = "/";
        }
        // Keep named path templates (e.g. {dealId}); only strip server/base prefixes.
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

    record ParameterFingerprint(String name, String in, boolean required)
            implements Comparable<ParameterFingerprint> {
        @Override
        public int compareTo(ParameterFingerprint other) {
            return Comparator.comparing(ParameterFingerprint::name)
                    .thenComparing(ParameterFingerprint::in)
                    .thenComparing(ParameterFingerprint::required)
                    .compare(this, other);
        }
    }

    record OperationFingerprint(
            Set<ParameterFingerprint> parameters,
            List<String> security,
            Set<String> statusCodes,
            Set<String> mediaTypes,
            Set<String> schemaRefs) {

        @SuppressWarnings("unchecked")
        static OperationFingerprint from(Map<String, Object> document, Map<?, ?> operation) {
            Set<ParameterFingerprint> parameters = new TreeSet<>();
            Object parametersNode = operation.get("parameters");
            if (parametersNode instanceof List<?> parameterList) {
                for (Object parameterNode : parameterList) {
                    if (!(parameterNode instanceof Map<?, ?> parameter)) {
                        continue;
                    }
                    Object ref = parameter.get("$ref");
                    if (ref instanceof String refValue) {
                        Map<?, ?> resolved = resolveParameterRef(document, refValue);
                        if (resolved != null) {
                            parameter = resolved;
                        }
                        else {
                            String local = refValue.substring(refValue.lastIndexOf('/') + 1);
                            parameters.add(new ParameterFingerprint(local, "ref", true));
                            continue;
                        }
                    }
                    Object name = parameter.get("name");
                    Object in = parameter.get("in");
                    if (!(name instanceof String nameValue) || !(in instanceof String inValue)) {
                        continue;
                    }
                    if (!Set.of("path", "query", "header", "cookie").contains(inValue)) {
                        continue;
                    }
                    boolean required = parameter.containsKey("required")
                            ? Boolean.TRUE.equals(parameter.get("required"))
                            : "path".equals(inValue);
                    parameters.add(new ParameterFingerprint(nameValue, inValue, required));
                }
            }

            List<String> security = normalizeSecurity(operation.get("security"));

            Set<String> statusCodes = new TreeSet<>();
            Set<String> mediaTypes = new TreeSet<>();
            Set<String> schemaRefs = new TreeSet<>();
            Object responsesNode = operation.get("responses");
            if (responsesNode instanceof Map<?, ?> responses) {
                for (Map.Entry<?, ?> responseEntry : responses.entrySet()) {
                    statusCodes.add(String.valueOf(responseEntry.getKey()));
                    if (responseEntry.getValue() instanceof Map<?, ?> response) {
                        collectContent(response, mediaTypes, schemaRefs);
                        Object responseRef = response.get("$ref");
                        if (responseRef instanceof String value) {
                            schemaRefs.add(localSchemaName(value));
                        }
                    }
                }
            }

            Object requestBody = operation.get("requestBody");
            if (requestBody instanceof Map<?, ?> body) {
                collectContent(body, mediaTypes, schemaRefs);
                Object bodyRef = body.get("$ref");
                if (bodyRef instanceof String value) {
                    schemaRefs.add(localSchemaName(value));
                }
            }

            return new OperationFingerprint(parameters, security, statusCodes, mediaTypes, schemaRefs);
        }

        @SuppressWarnings("unchecked")
        private static Map<?, ?> resolveParameterRef(Map<String, Object> document, String ref) {
            if (!ref.startsWith("#/components/parameters/")) {
                return null;
            }
            String local = ref.substring("#/components/parameters/".length());
            Object componentsNode = document.get("components");
            if (!(componentsNode instanceof Map<?, ?> components)) {
                return null;
            }
            Object parametersNode = components.get("parameters");
            if (!(parametersNode instanceof Map<?, ?> parameters)) {
                return null;
            }
            Object resolved = parameters.get(local);
            return resolved instanceof Map<?, ?> map ? map : null;
        }

        @SuppressWarnings("unchecked")
        private static void collectContent(
                Map<?, ?> node, Set<String> mediaTypes, Set<String> schemaRefs) {
            Object contentNode = node.get("content");
            if (!(contentNode instanceof Map<?, ?> content)) {
                return;
            }
            for (Map.Entry<?, ?> mediaEntry : content.entrySet()) {
                mediaTypes.add(String.valueOf(mediaEntry.getKey()));
                if (mediaEntry.getValue() instanceof Map<?, ?> media) {
                    String ref = schemaRef(media.get("schema"));
                    if (ref != null) {
                        schemaRefs.add(ref);
                    }
                }
            }
        }

        @SuppressWarnings("unchecked")
        private static String schemaRef(Object schemaNode) {
            if (!(schemaNode instanceof Map<?, ?> schema)) {
                return null;
            }
            Object ref = schema.get("$ref");
            if (ref instanceof String value) {
                return localSchemaName(value);
            }
            for (String key : List.of("allOf", "oneOf", "anyOf")) {
                Object composition = schema.get(key);
                if (composition instanceof List<?> items) {
                    for (Object item : items) {
                        String nested = schemaRef(item);
                        if (nested != null) {
                            return nested;
                        }
                    }
                }
            }
            return schemaRef(schema.get("items"));
        }

        private static String localSchemaName(String ref) {
            int slash = ref.lastIndexOf('/');
            return slash >= 0 ? ref.substring(slash + 1) : ref;
        }

        @SuppressWarnings("unchecked")
        private static List<String> normalizeSecurity(Object securityNode) {
            if (securityNode == null) {
                return List.of();
            }
            if (!(securityNode instanceof List<?> requirements)) {
                return List.of("__invalid__");
            }
            List<String> normalized = new ArrayList<>();
            for (Object requirementNode : requirements) {
                if (!(requirementNode instanceof Map<?, ?> requirement) || requirement.isEmpty()) {
                    normalized.add("__empty__");
                    continue;
                }
                List<String> names = requirement.keySet().stream()
                        .map(String::valueOf)
                        .sorted()
                        .toList();
                for (String name : names) {
                    Object scopesNode = requirement.get(name);
                    List<String> scopes = scopesNode instanceof List<?> list
                            ? list.stream().map(String::valueOf).sorted().toList()
                            : List.of(String.valueOf(scopesNode));
                    normalized.add(name + scopes);
                }
            }
            normalized.sort(Comparator.naturalOrder());
            return List.copyOf(normalized);
        }
    }
}
