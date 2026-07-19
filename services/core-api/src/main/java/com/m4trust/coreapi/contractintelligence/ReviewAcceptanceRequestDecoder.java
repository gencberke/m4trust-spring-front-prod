package com.m4trust.coreapi.contractintelligence;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

/** Strict JSON boundary for review acceptance. Everything beyond this class is typed. */
final class ReviewAcceptanceRequestDecoder {
    private static final Set<String> ROOT_FIELDS = Set.of("analysisId", "expectedVersion", "decisions");
    private static final Set<String> REFERENCE_DECISION_FIELDS = Set.of("decision", "ruleReference");
    private static final Set<String> EDITED_DECISION_FIELDS = Set.of(
            "decision", "ruleReference", "category", "title", "description", "structuredValue");
    private static final Set<String> ADDED_DECISION_FIELDS = Set.of(
            "decision", "category", "title", "description", "structuredValue");
    private static final Set<String> CATEGORIES = Set.of(
            "PAYMENT", "DELIVERY", "QUALITY", "PENALTY", "TERMINATION", "DISPUTE", "OTHER", "UNKNOWN");

    record Request(UUID analysisId, long expectedVersion, List<Decision> decisions) { }
    sealed interface Decision permits Kept, Modified, Excluded, Added { }
    record Kept(String ruleReference) implements Decision { }
    record Excluded(String ruleReference) implements Decision { }
    record Modified(String ruleReference, EditableRule rule) implements Decision { }
    record Added(EditableRule rule) implements Decision { }
    record EditableRule(String category, String title, String description,
            ReviewDtos.StructuredValue structuredValue) { }

    Request decode(JsonNode root) {
        requireObjectWith(root, ROOT_FIELDS);
        UUID analysisId = uuid(root.get("analysisId"));
        long expectedVersion = nonNegativeLong(root.get("expectedVersion"));
        JsonNode decisionsNode = root.get("decisions");
        if (!decisionsNode.isArray()) {
            throw malformed();
        }
        List<Decision> decisions = new ArrayList<>();
        for (int index = 0; index < decisionsNode.size(); index++) {
            try {
                decisions.add(decodeDecision(decisionsNode.get(index)));
            } catch (AnalysisExceptions.Validation exception) {
                throw validation("decisions[" + index + "]." + exception.field());
            }
        }
        return new Request(analysisId, expectedVersion, List.copyOf(decisions));
    }

    ReviewDtos.ExtractedRule decodeExtractedRule(JsonNode node) {
        requireObject(node);
        String reference = requiredText(node.get("ruleReference"));
        String category = requiredText(node.get("category"));
        String title = requiredText(node.get("title"));
        String description = requiredText(node.get("description"));
        if (!node.path("confidence").isNumber() || !node.path("sourceReferences").isArray()) {
            throw malformed();
        }
        List<Object> sources = new ArrayList<>();
        for (JsonNode source : node.get("sourceReferences")) {
            sources.add(source);
        }
        return new ReviewDtos.ExtractedRule(reference, category, title,
                description, structuredValue(node.get("structuredValue"), false), node.get("confidence").decimalValue(),
                List.copyOf(sources), legalBasis(node.get("legalBasis")));
    }

    ReviewDtos.RuleSetRule decodeRuleSetRule(JsonNode node) {
        requireObjectWith(node, Set.of("ruleReference", "decision", "category", "title", "description",
                "structuredValue", "legalBasis", "legalBasisProvenance"));
        String decision = requiredText(node.get("decision"));
        String provenance = requiredText(node.get("legalBasisProvenance"));
        if (!Set.of("KEPT", "MODIFIED", "ADDED").contains(decision)
                || !Set.of("EXTRACTED", "REVIEWER_MODIFIED", "MANUALLY_ADDED").contains(provenance)) {
            throw malformed();
        }
        EditableRule editable = editableRule(node, true);
        return new ReviewDtos.RuleSetRule(requiredText(node.get("ruleReference")), decision,
                editable.category(), editable.title(), editable.description(), editable.structuredValue(),
                legalBasis(node.get("legalBasis")), provenance);
    }

    String canonicalHash(UUID entityId, UUID dealId, Request request) {
        CanonicalWriter writer = new CanonicalWriter();
        writer.field("entityId", entityId.toString());
        writer.field("dealId", dealId.toString());
        writer.field("analysisId", request.analysisId().toString());
        writer.number("expectedVersion", request.expectedVersion());
        writer.number("decisionCount", request.decisions().size());
        for (Decision decision : request.decisions()) {
            writeDecision(writer, decision);
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(writer.value().getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private Decision decodeDecision(JsonNode node) {
        if (!node.isObject() || !node.path("decision").isTextual()) {
            throw malformed();
        }
        return switch (node.get("decision").asText()) {
            case "KEPT" -> new Kept(referenceDecision(node));
            case "EXCLUDED" -> new Excluded(referenceDecision(node));
            case "MODIFIED" -> {
                requireObjectWith(node, EDITED_DECISION_FIELDS);
                yield new Modified(requiredText(node.get("ruleReference")), editableRule(node, true));
            }
            case "ADDED" -> {
                requireObjectWith(node, ADDED_DECISION_FIELDS);
                yield new Added(editableRule(node, true));
            }
            default -> throw malformed();
        };
    }

    private String referenceDecision(JsonNode node) {
        requireObjectWith(node, REFERENCE_DECISION_FIELDS);
        return requiredText(node.get("ruleReference"));
    }

    private EditableRule editableRule(JsonNode node, boolean requestValue) {
        String category = requiredText(node.get("category"));
        if (!CATEGORIES.contains(category)) {
            throw validation("category");
        }
        String title = requiredText(node.get("title"));
        String description = requiredText(node.get("description"));
        if (title.isBlank() || title.length() > 500) {
            throw validation("title");
        }
        if (description.isBlank() || description.length() > 4000) {
            throw validation("description");
        }
        return new EditableRule(category, title, description, structuredValue(node.get("structuredValue"), requestValue));
    }

    private ReviewDtos.StructuredValue structuredValue(JsonNode value, boolean finalBoundary) {
        if (!value.isObject() || !value.path("type").isTextual()) {
            throw malformed();
        }
        return switch (value.get("type").asText()) {
            case "TEXT" -> {
                requireObjectWith(value, Set.of("type", "value"));
                yield new ReviewDtos.TextValue("TEXT", requiredText(value.get("value")));
            }
            case "MONEY" -> {
                requireObjectWith(value, Set.of("type", "amountMinor", "currency"));
                long amount = integer(value.get("amountMinor"), "structuredValue.amountMinor");
                if (finalBoundary && amount < 0) throw validation("structuredValue.amountMinor");
                String currency = requiredText(value.get("currency"));
                if (finalBoundary && !currency.matches("^[A-Z]{3}$")) throw validation("structuredValue.currency");
                yield new ReviewDtos.MoneyValue("MONEY", amount, currency);
            }
            case "PERCENTAGE" -> {
                requireObjectWith(value, Set.of("type", "basisPoints"));
                long basisPoints = integer(value.get("basisPoints"), "structuredValue.basisPoints");
                if (finalBoundary && (basisPoints < 0 || basisPoints > 10_000)) throw validation("structuredValue.basisPoints");
                yield new ReviewDtos.PercentageValue("PERCENTAGE", (int) basisPoints);
            }
            case "DURATION" -> {
                requireObjectWith(value, Set.of("type", "valueSeconds"));
                long seconds = integer(value.get("valueSeconds"), "structuredValue.valueSeconds");
                if (finalBoundary && seconds < 0) throw validation("structuredValue.valueSeconds");
                yield new ReviewDtos.DurationValue("DURATION", seconds);
            }
            case "DATE" -> {
                requireObjectWith(value, Set.of("type", "value"));
                String text = requiredText(value.get("value"));
                if (finalBoundary) {
                    try { LocalDate.parse(text); }
                    catch (RuntimeException exception) { throw validation("structuredValue.value"); }
                }
                yield new ReviewDtos.DateValue("DATE", text);
            }
            case "BOOLEAN" -> {
                requireObjectWith(value, Set.of("type", "value"));
                if (!value.path("value").isBoolean()) throw malformed();
                yield new ReviewDtos.BooleanValue("BOOLEAN", value.get("value").asBoolean());
            }
            case "QUANTITY" -> {
                requireObjectWith(value, Set.of("type", "value", "unit"));
                if (!value.path("value").isNumber()) throw malformed();
                String unit = requiredText(value.get("unit"));
                if (finalBoundary && unit.isBlank()) throw validation("structuredValue.unit");
                yield new ReviewDtos.QuantityValue("QUANTITY", value.get("value").decimalValue(), unit);
            }
            default -> throw malformed();
        };
    }

    private ReviewDtos.LegalBasis legalBasis(JsonNode node) {
        if (node == null || node.isNull()) return null;
        requireObjectWith(node, Set.of("source", "articleNo"));
        return new ReviewDtos.LegalBasis(requiredText(node.get("source")), requiredText(node.get("articleNo")));
    }

    private static long integer(JsonNode node, String field) {
        if (node == null || !node.isIntegralNumber() || !node.canConvertToLong()) {
            throw malformed();
        }
        return node.asLong();
    }

    private static long nonNegativeLong(JsonNode node) {
        long value = integer(node, "expectedVersion");
        if (value < 0) throw malformed();
        return value;
    }

    private static UUID uuid(JsonNode node) {
        try {
            return UUID.fromString(requiredText(node));
        } catch (IllegalArgumentException exception) {
            throw malformed();
        }
    }

    private static String requiredText(JsonNode node) {
        if (node == null || !node.isTextual()) throw malformed();
        return node.asText();
    }

    private static void requireObject(JsonNode node) {
        if (node == null || !node.isObject()) throw malformed();
    }

    private static void requireObjectWith(JsonNode node, Set<String> expected) {
        requireObject(node);
        Set<String> actual = new java.util.HashSet<>();
        node.properties().forEach(entry -> actual.add(entry.getKey()));
        if (!actual.equals(expected)) throw malformed();
    }

    private static AnalysisExceptions.MalformedRequest malformed() {
        return new AnalysisExceptions.MalformedRequest();
    }

    private static AnalysisExceptions.Validation validation(String field) {
        return new AnalysisExceptions.Validation(field);
    }

    static void validateFinalValue(ReviewDtos.StructuredValue value) {
        if (value instanceof ReviewDtos.MoneyValue money) {
            if (money.amountMinor() < 0) throw validation("structuredValue.amountMinor");
            if (!money.currency().matches("^[A-Z]{3}$")) throw validation("structuredValue.currency");
        } else if (value instanceof ReviewDtos.PercentageValue percentage) {
            if (percentage.basisPoints() < 0 || percentage.basisPoints() > 10_000) throw validation("structuredValue.basisPoints");
        } else if (value instanceof ReviewDtos.DurationValue duration) {
            if (duration.valueSeconds() < 0) throw validation("structuredValue.valueSeconds");
        } else if (value instanceof ReviewDtos.DateValue date) {
            try { LocalDate.parse(date.value()); }
            catch (RuntimeException exception) { throw validation("structuredValue.value"); }
        } else if (value instanceof ReviewDtos.QuantityValue quantity && quantity.unit().isBlank()) {
            throw validation("structuredValue.unit");
        }
    }

    private static void writeDecision(CanonicalWriter writer, Decision decision) {
        if (decision instanceof Kept kept) {
            writer.field("decision", "KEPT");
            writer.field("ruleReference", kept.ruleReference());
        } else if (decision instanceof Excluded excluded) {
            writer.field("decision", "EXCLUDED");
            writer.field("ruleReference", excluded.ruleReference());
        } else if (decision instanceof Modified modified) {
            writer.field("decision", "MODIFIED");
            writer.field("ruleReference", modified.ruleReference());
            writeEditable(writer, modified.rule());
        } else if (decision instanceof Added added) {
            writer.field("decision", "ADDED");
            writeEditable(writer, added.rule());
        }
    }

    private static void writeEditable(CanonicalWriter writer, EditableRule rule) {
        writer.field("category", rule.category());
        writer.field("title", rule.title());
        writer.field("description", rule.description());
        writeValue(writer, rule.structuredValue());
    }

    private static void writeValue(CanonicalWriter writer, ReviewDtos.StructuredValue value) {
        if (value instanceof ReviewDtos.TextValue text) { writer.field("type", text.type()); writer.field("value", text.value()); }
        else if (value instanceof ReviewDtos.MoneyValue money) { writer.field("type", money.type()); writer.number("amountMinor", money.amountMinor()); writer.field("currency", money.currency()); }
        else if (value instanceof ReviewDtos.PercentageValue percentage) { writer.field("type", percentage.type()); writer.number("basisPoints", percentage.basisPoints()); }
        else if (value instanceof ReviewDtos.DurationValue duration) { writer.field("type", duration.type()); writer.number("valueSeconds", duration.valueSeconds()); }
        else if (value instanceof ReviewDtos.DateValue date) { writer.field("type", date.type()); writer.field("value", date.value()); }
        else if (value instanceof ReviewDtos.BooleanValue bool) { writer.field("type", bool.type()); writer.field("value", Boolean.toString(bool.value())); }
        else if (value instanceof ReviewDtos.QuantityValue quantity) { writer.field("type", quantity.type()); writer.field("value", quantity.value().toPlainString()); writer.field("unit", quantity.unit()); }
    }

    private static final class CanonicalWriter {
        private final StringBuilder text = new StringBuilder();
        void field(String name, String value) { append(name); append(value); }
        void number(String name, long value) { field(name, Long.toString(value)); }
        String value() { return text.toString(); }
        private void append(String value) { text.append(value.length()).append(':').append(value).append('|'); }
    }
}
