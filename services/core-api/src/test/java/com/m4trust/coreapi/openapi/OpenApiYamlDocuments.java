package com.m4trust.coreapi.openapi;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.core.io.ClassPathResource;
import org.yaml.snakeyaml.Yaml;

/**
 * Loads OpenAPI YAML while protecting path-template braces from SnakeYAML
 * flow-mapping parsing ({@code {dealId}} must stay a literal path segment).
 */
final class OpenApiYamlDocuments {

    private static final Pattern PATH_PARAM = Pattern.compile("\\{([A-Za-z][A-Za-z0-9_]*)\\}");
    private static final String PREFIX = "__OPENAPI_PARAM_";
    private static final String SUFFIX = "__";

    private OpenApiYamlDocuments() {
    }

    static Map<String, Object> loadCoreApiV1() throws IOException {
        ClassPathResource classpath = new ClassPathResource("contracts/openapi/core-api-v1.yaml");
        if (classpath.exists()) {
            try (InputStream input = classpath.getInputStream()) {
                return parse(new String(input.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        Path filesystem = Path.of("..", "..", "contracts", "openapi", "core-api-v1.yaml")
                .toAbsolutePath()
                .normalize();
        return parse(Files.readString(filesystem, StandardCharsets.UTF_8));
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> parse(String yamlText) {
        String protectedYaml = protectPathParams(yamlText);
        Object loaded = new Yaml().load(protectedYaml);
        if (!(loaded instanceof Map<?, ?> map)) {
            throw new IllegalStateException("OpenAPI document must be a mapping");
        }
        return (Map<String, Object>) restorePathParams(map);
    }

    private static String protectPathParams(String yamlText) {
        Matcher matcher = PATH_PARAM.matcher(yamlText);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(out,
                    Matcher.quoteReplacement(PREFIX + matcher.group(1) + SUFFIX));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private static Object restorePathParams(Object node) {
        if (node instanceof Map<?, ?> map) {
            Map<String, Object> restored = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                restored.put(restoreToken(String.valueOf(entry.getKey())),
                        restorePathParams(entry.getValue()));
            }
            return restored;
        }
        if (node instanceof List<?> list) {
            List<Object> restored = new ArrayList<>(list.size());
            for (Object item : list) {
                restored.add(restorePathParams(item));
            }
            return restored;
        }
        if (node instanceof String value) {
            return restoreToken(value);
        }
        return node;
    }

    private static String restoreToken(String value) {
        Matcher matcher = Pattern.compile(Pattern.quote(PREFIX) + "([A-Za-z][A-Za-z0-9_]*)"
                + Pattern.quote(SUFFIX)).matcher(value);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(out,
                    Matcher.quoteReplacement("{" + matcher.group(1) + "}"));
        }
        matcher.appendTail(out);
        return out.toString();
    }
}
