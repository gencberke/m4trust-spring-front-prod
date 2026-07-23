package com.m4trust.coreapi.ratification;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonInclude;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/** Pure snapshot assembler: only this closed value is passed to the JCS hasher. */
@Component
final class RatificationSnapshotAssembler {
    static final long MAX_SAFE_INTEGER = 9007199254740991L;

    private static final Set<String> DECISIONS = Set.of("KEPT", "MODIFIED", "ADDED");
    private static final Set<String> CATEGORIES = Set.of(
            "PAYMENT", "DELIVERY", "QUALITY", "PENALTY", "TERMINATION", "DISPUTE", "OTHER", "UNKNOWN");
    private static final Set<String> PROVENANCE = Set.of("EXTRACTED", "REVIEWER_MODIFIED", "MANUALLY_ADDED");
    private static final Set<String> LEGAL_SOURCES = Set.of(
            "tbk-6098", "kvkk-6698", "odeme-hizmetleri-6493", "aml-5549",
            "odeme-hizmetleri-yonetmelik", "odeme-hizmetleri-tebligi");

    private final ObjectMapper json;
    private final CanonicalSnapshotHasher hasher;

    RatificationSnapshotAssembler(ObjectMapper json, CanonicalSnapshotHasher hasher) {
        this.json = json;
        this.hasher = hasher;
    }

    Result assemble(
            RatificationSourcePorts.Target target,
            RatificationSourcePorts.Document document,
            RatificationSourcePorts.RuleSet ruleSet,
            long amountMinor,
            String currency) {
        return assemble(target, document, ruleSet, amountMinor, currency, null);
    }

    Result assemble(
            RatificationSourcePorts.Target target,
            RatificationSourcePorts.Document document,
            RatificationSourcePorts.RuleSet ruleSet,
            long amountMinor,
            String currency,
            Integer disputeWindowDays) {
        if (amountMinor < 1 || amountMinor > MAX_SAFE_INTEGER || currency == null || !currency.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException("Invalid exact commercial terms");
        }
        if (!target.dealId().equals(document.dealId())
                || !target.dealId().equals(ruleSet.dealId())
                || !Objects.equals(target.currentDocumentId(), document.documentId())
                || !Objects.equals(target.currentRuleSetId(), ruleSet.ruleSetVersionId())
                || ruleSet.version() < 1
                || ruleSet.version() > MAX_SAFE_INTEGER
                || target.buyer() == null
                || target.seller() == null
                || target.buyer().legalEntityId().equals(target.seller().legalEntityId())) {
            throw new IllegalArgumentException("Invalid snapshot sources");
        }

        text(target.reference(), 1, 50);
        text(target.title(), 1, 200);
        text(document.objectVersion(), 1, 512);
        List<RatificationSourcePorts.Rule> rules = new ArrayList<>(ruleSet.rules());
        rules.sort((left, right) -> Arrays.compareUnsigned(
                left.ruleReference().getBytes(StandardCharsets.UTF_8), right.ruleReference().getBytes(StandardCharsets.UTF_8)));
        Set<String> seen = new HashSet<>();
        for (RatificationSourcePorts.Rule rule : rules) {
            if (!seen.add(rule.ruleReference())) {
                throw new IllegalArgumentException("Duplicate ruleReference");
            }
        }

        int schemaVersion = disputeWindowDays == null ? 1 : 2;
        if (disputeWindowDays != null && (disputeWindowDays < 0 || disputeWindowDays > 365)) {
            throw new IllegalArgumentException("Invalid disputeWindowDays");
        }
        Snapshot snapshot = new Snapshot(
                schemaVersion,
                uuid(target.dealId()),
                target.reference(),
                target.title(),
                party(target.buyer()),
                party(target.seller()),
                new RuleSet(uuid(ruleSet.ruleSetVersionId()), ruleSet.version(), rules.stream().map(this::rule).toList()),
                new Terms(amountMinor, currency),
                new Document(uuid(document.documentId()), document.objectVersion(), hex(document.sha256())),
                disputeWindowDays);
        try {
            String serialized = json.writeValueAsString(snapshot);
            return new Result(snapshot, serialized, hasher.hash(serialized));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private Party party(RatificationSourcePorts.Party party) {
        text(party.legalName(), 1, 200);
        return new Party(uuid(party.legalEntityId()), party.legalName());
    }

    private Rule rule(RatificationSourcePorts.Rule rule) {
        text(rule.ruleReference(), 1, Integer.MAX_VALUE);
        text(rule.title(), 1, 500);
        text(rule.description(), 1, 4000);
        if (!DECISIONS.contains(rule.decision())
                || !CATEGORIES.contains(rule.category())
                || !PROVENANCE.contains(rule.legalBasisProvenance())) {
            throw new IllegalArgumentException("Invalid rule enum");
        }
        validateStructuredValue(rule.structuredValue());
        validateLegalBasis(rule.legalBasis());
        return new Rule(
                rule.ruleReference(),
                rule.decision(),
                rule.category(),
                rule.title(),
                rule.description(),
                rule.structuredValue().deepCopy(),
                rule.legalBasis() == null ? null : rule.legalBasis().deepCopy(),
                rule.legalBasisProvenance());
    }

    private static void validateStructuredValue(JsonNode value) {
        if (value == null || !value.isObject()) {
            throw new IllegalArgumentException("Invalid structured value");
        }
        safe(value);
        String type = requiredText(value, "type");
        switch (type) {
            case "TEXT" -> {
                exactFields(value, Set.of("type", "value"));
                requireText(value, "value");
            }
            case "MONEY" -> {
                exactFields(value, Set.of("type", "amountMinor", "currency"));
                requireIntegralAtLeast(value, "amountMinor", 0);
                String currency = requiredText(value, "currency");
                if (!currency.matches("[A-Z]{3}")) {
                    throw new IllegalArgumentException("Invalid money currency");
                }
            }
            case "PERCENTAGE" -> {
                exactFields(value, Set.of("type", "basisPoints"));
                requireIntegralRange(value, "basisPoints", 0, 10000);
            }
            case "DURATION" -> {
                exactFields(value, Set.of("type", "valueSeconds"));
                requireIntegralAtLeast(value, "valueSeconds", 0);
            }
            case "DATE" -> {
                exactFields(value, Set.of("type", "value"));
                try {
                    LocalDate.parse(requiredText(value, "value"));
                } catch (DateTimeParseException exception) {
                    throw new IllegalArgumentException("Invalid ISO local date", exception);
                }
            }
            case "BOOLEAN" -> {
                exactFields(value, Set.of("type", "value"));
                if (!value.get("value").isBoolean()) {
                    throw new IllegalArgumentException("Invalid boolean value");
                }
            }
            case "QUANTITY" -> {
                exactFields(value, Set.of("type", "value", "unit"));
                if (!value.get("value").isNumber()) {
                    throw new IllegalArgumentException("Invalid quantity value");
                }
                text(requiredText(value, "unit"), 1, Integer.MAX_VALUE);
            }
            default -> throw new IllegalArgumentException("Invalid structured value type");
        }
    }

    private static void validateLegalBasis(JsonNode value) {
        if (value == null || value.isNull()) {
            return;
        }
        if (!value.isObject()) {
            throw new IllegalArgumentException("Invalid legal basis");
        }
        safe(value);
        exactFields(value, Set.of("source", "articleNo"));
        if (!LEGAL_SOURCES.contains(requiredText(value, "source"))) {
            throw new IllegalArgumentException("Invalid legal basis source");
        }
        text(requiredText(value, "articleNo"), 1, 32);
    }

    private static void exactFields(JsonNode node, Set<String> expected) {
        if (!new HashSet<>(node.propertyNames()).equals(expected)) {
            throw new IllegalArgumentException("Unexpected structured object fields");
        }
    }

    private static String requiredText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual()) {
            throw new IllegalArgumentException("Expected text field " + field);
        }
        return value.textValue();
    }

    private static void requireText(JsonNode node, String field) {
        requiredText(node, field);
    }

    private static void requireIntegralAtLeast(JsonNode node, String field, long minimum) {
        requireIntegralRange(node, field, minimum, MAX_SAFE_INTEGER);
    }

    private static void requireIntegralRange(JsonNode node, String field, long minimum, long maximum) {
        JsonNode value = node.get(field);
        if (value == null || !value.isIntegralNumber()
                || value.bigIntegerValue().compareTo(BigInteger.valueOf(minimum)) < 0
                || value.bigIntegerValue().compareTo(BigInteger.valueOf(maximum)) > 0) {
            throw new IllegalArgumentException("Invalid integral field " + field);
        }
    }

    private static String uuid(UUID id) {
        String value = Objects.requireNonNull(id).toString();
        if (!value.matches("[0-9a-f-]{36}")) {
            throw new IllegalArgumentException("Invalid UUID");
        }
        return value;
    }

    private static String hex(String value) {
        if (value == null || !value.matches("[a-f0-9]{64}")) {
            throw new IllegalArgumentException("Invalid sha256");
        }
        return value;
    }

    private static void text(String value, int minimum, int maximum) {
        if (value == null || value.length() < minimum || value.length() > maximum) {
            throw new IllegalArgumentException("Invalid snapshot text");
        }
    }

    private static void safe(JsonNode value) {
        if (value.isIntegralNumber()
                && (value.bigIntegerValue().compareTo(BigInteger.valueOf(MAX_SAFE_INTEGER)) > 0
                || value.bigIntegerValue().compareTo(BigInteger.valueOf(MAX_SAFE_INTEGER).negate()) < 0)) {
            throw new IllegalArgumentException("Unsafe integer");
        }
        value.forEach(RatificationSnapshotAssembler::safe);
    }

    record Result(Snapshot snapshot, String serializedSnapshot, String contentHash) { }

    record Snapshot(
            int schemaVersion,
            String dealId,
            String dealReference,
            String dealTitle,
            Party buyer,
            Party seller,
            RuleSet ruleSet,
            Terms commercialTerms,
            Document document,
            @JsonInclude(JsonInclude.Include.NON_NULL) Integer disputeWindowDays) { }

    record Party(String legalEntityId, String legalName) { }

    record RuleSet(String ruleSetVersionId, long version, List<Rule> rules) {
        RuleSet {
            rules = List.copyOf(rules);
        }
    }

    record Rule(
            String ruleReference,
            String decision,
            String category,
            String title,
            String description,
            JsonNode structuredValue,
            JsonNode legalBasis,
            String legalBasisProvenance) {
        Rule {
            structuredValue = structuredValue.deepCopy();
            legalBasis = legalBasis == null ? null : legalBasis.deepCopy();
        }

        @Override
        public JsonNode structuredValue() {
            return structuredValue.deepCopy();
        }

        @Override
        public JsonNode legalBasis() {
            return legalBasis == null ? null : legalBasis.deepCopy();
        }
    }

    record Terms(long amountMinor, String currency) { }

    record Document(String documentId, String objectVersion, String sha256) { }
}
