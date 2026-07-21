package com.m4trust.coreapi.integration.messaging;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Small Draft 2020-12 evaluator for the vocabulary used by the committed AI
 * schemas. It deliberately evaluates those resources themselves (including
 * refs and unions), so contract changes cannot silently leave a handwritten
 * event DTO validator behind.
 */
@Component
public final class CommittedEventSchemaValidator {

    private static final String SCHEMA_ROOT = "contracts/schemas/";
    private static final String ID_PREFIX = "https://schemas.m4trust.internal/ai/";
    private final ObjectMapper mapper;
    private final Map<String, JsonNode> schemas = new ConcurrentHashMap<>();

    public CommittedEventSchemaValidator(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public void validateDocumentCompleted(JsonNode event) {
        validate(event, "document-extraction/completed-event-1.0.0.schema.json");
    }

    public void validateDocumentFailed(JsonNode event) {
        validate(event, "document-extraction/failed-event-1.0.0.schema.json");
    }

    public void validateVideoCompleted(JsonNode event) {
        validate(event, "video-analysis/completed-event-1.0.0.schema.json");
    }

    public void validateVideoFailed(JsonNode event) {
        validate(event, "video-analysis/failed-event-1.0.0.schema.json");
    }

    private void validate(JsonNode event, String resource) {
        List<String> errors = new ArrayList<>();
        evaluate(event, load(resource), resource, errors, "$");
        if (!errors.isEmpty()) {
            throw new ContractViolationException("AI event violates committed schema: " + errors);
        }
    }

    private boolean evaluate(JsonNode value, JsonNode schema, String resource,
            List<String> errors, String path) {
        if (schema.has("$ref")) {
            return evaluate(value, reference(schema.get("$ref").asString(), resource),
                    resourceFor(schema.get("$ref").asString(), resource), errors, path);
        }
        if (schema.has("allOf")) {
            for (JsonNode child : schema.get("allOf")) {
                evaluate(value, child, resource, errors, path);
            }
        }
        if (schema.has("oneOf")) {
            int matches = 0;
            for (JsonNode child : schema.get("oneOf")) {
                List<String> trial = new ArrayList<>();
                evaluate(value, child, resource, trial, path);
                if (trial.isEmpty()) {
                    matches++;
                }
            }
            if (matches != 1) {
                errors.add(path + ": oneOf");
            }
        }
        if (schema.has("const") && !schema.get("const").equals(value)) {
            errors.add(path + ": const");
        }
        if (schema.has("enum")) {
            boolean match = false;
            for (JsonNode allowed : schema.get("enum")) {
                if (allowed.equals(value)) {
                    match = true;
                }
            }
            if (!match) {
                errors.add(path + ": enum");
            }
        }
        if (schema.has("type") && !typeMatches(value, schema.get("type"))) {
            errors.add(path + ": type");
            return false;
        }
        if (value.isObject()) {
            object(value, schema, resource, errors, path);
        }
        if (value.isArray() && schema.has("items")) {
            for (int i = 0; i < value.size(); i++) {
                evaluate(value.get(i), schema.get("items"), resource, errors, path + "[" + i + "]");
            }
        }
        if (value.isArray()) {
            if (schema.has("minItems") && value.size() < schema.get("minItems").asInt()) {
                errors.add(path + ": minItems");
            }
            if (schema.has("maxItems") && value.size() > schema.get("maxItems").asInt()) {
                errors.add(path + ": maxItems");
            }
        }
        scalar(value, schema, errors, path);
        return errors.isEmpty();
    }

    private void object(JsonNode value, JsonNode schema, String resource, List<String> errors, String path) {
        if (schema.has("required")) {
            for (JsonNode required : schema.get("required")) {
                if (!value.has(required.asString())) {
                    errors.add(path + ": required");
                }
            }
        }
        JsonNode properties = schema.get("properties");
        if (properties != null) {
            Iterator<Map.Entry<String, JsonNode>> it = properties.properties().iterator();
            while (it.hasNext()) {
                var entry = it.next();
                if (value.has(entry.getKey())) {
                    evaluate(value.get(entry.getKey()), entry.getValue(), resource, errors,
                            path + "." + entry.getKey());
                }
            }
            if (schema.has("additionalProperties") && !schema.get("additionalProperties").asBoolean()) {
                Set<String> known = new HashSet<>();
                properties.properties().forEach(e -> known.add(e.getKey()));
                value.properties().forEach(e -> {
                    if (!known.contains(e.getKey())) {
                        errors.add(path + ": additionalProperties");
                    }
                });
            }
        }
    }

    private static boolean typeMatches(JsonNode value, JsonNode type) {
        if (type.isArray()) {
            for (JsonNode t : type) {
                if (typeMatches(value, t)) {
                    return true;
                }
            }
            return false;
        }
        return switch (type.asString()) {
            case "object" -> value.isObject();
            case "array" -> value.isArray();
            case "string" -> value.isString();
            case "integer" -> value.isIntegralNumber();
            case "number" -> value.isNumber();
            case "boolean" -> value.isBoolean();
            case "null" -> value.isNull();
            default -> false;
        };
    }

    private static void scalar(JsonNode value, JsonNode schema, List<String> errors, String path) {
        if (value.isTextual()) {
            String text = value.asString();
            if (schema.has("minLength") && text.length() < schema.get("minLength").asInt()) {
                errors.add(path + ": minLength");
            }
            if (schema.has("maxLength") && text.length() > schema.get("maxLength").asInt()) {
                errors.add(path + ": maxLength");
            }
            if (schema.has("pattern")
                    && !Pattern.compile(schema.get("pattern").asString()).matcher(text).find()) {
                errors.add(path + ": pattern");
            }
            if (schema.has("format") && !format(text, schema.get("format").asString())) {
                errors.add(path + ": format");
            }
        }
        if (value.isNumber()) {
            BigDecimal number = value.decimalValue();
            if (schema.has("minimum") && number.compareTo(schema.get("minimum").decimalValue()) < 0) {
                errors.add(path + ": minimum");
            }
            if (schema.has("maximum") && number.compareTo(schema.get("maximum").decimalValue()) > 0) {
                errors.add(path + ": maximum");
            }
        }
    }

    private static boolean format(String value, String format) {
        try {
            switch (format) {
                case "uuid" -> UUID.fromString(value);
                case "date" -> LocalDate.parse(value);
                case "date-time" -> OffsetDateTime.parse(value);
                default -> {
                    return true;
                }
            }
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private JsonNode reference(String reference, String currentResource) {
        if (reference.startsWith("#/$defs/")) {
            return load(currentResource).get("$defs").get(reference.substring(8));
        }
        return load(resourceFor(reference, currentResource));
    }

    private String resourceFor(String reference, String currentResource) {
        if (reference.startsWith("#")) {
            return currentResource;
        }
        if (!reference.startsWith(ID_PREFIX)) {
            throw new ContractViolationException("Unsupported schema reference");
        }
        String identifier = reference.substring(ID_PREFIX.length());
        int slash = identifier.lastIndexOf('/');
        if (slash < 0) {
            throw new ContractViolationException("Unsupported schema reference");
        }
        return identifier.substring(0, slash) + "-" + identifier.substring(slash + 1) + ".schema.json";
    }

    private JsonNode load(String resource) {
        return schemas.computeIfAbsent(resource, key -> {
            try (InputStream input = getClass().getClassLoader().getResourceAsStream(SCHEMA_ROOT + key)) {
                if (input == null) {
                    throw new IllegalStateException("Committed schema resource is missing");
                }
                JsonNode schema = mapper.readTree(input);
                ensureSupported(schema);
                return schema;
            } catch (IOException exception) {
                throw new IllegalStateException("Committed schema cannot be read", exception);
            }
        });
    }

    /** Any future schema vocabulary is rejected at startup/first use, never ignored. */
    private void ensureSupported(JsonNode schema) {
        Set<String> supported = Set.of("$schema", "$id", "$defs", "$ref", "title", "description",
                "type", "additionalProperties", "properties", "required", "enum", "pattern", "format",
                "allOf", "oneOf", "const", "items", "minimum", "maximum", "minLength", "maxLength",
                "minItems", "maxItems");
        schema.properties().forEach(entry -> {
            if (!supported.contains(entry.getKey())) {
                throw new ContractViolationException("Unsupported schema vocabulary");
            }
        });
        if (schema.has("properties")) {
            schema.get("properties").properties().forEach(entry -> ensureSupported(entry.getValue()));
        }
        if (schema.has("$defs")) {
            schema.get("$defs").properties().forEach(entry -> ensureSupported(entry.getValue()));
        }
        for (String combinator : List.of("allOf", "oneOf")) {
            if (schema.has(combinator)) {
                for (JsonNode child : schema.get(combinator)) {
                    ensureSupported(child);
                }
            }
        }
        if (schema.has("items")) {
            ensureSupported(schema.get("items"));
        }
    }

    public static final class ContractViolationException extends RuntimeException {
        ContractViolationException(String message) {
            super(message);
        }
    }
}
