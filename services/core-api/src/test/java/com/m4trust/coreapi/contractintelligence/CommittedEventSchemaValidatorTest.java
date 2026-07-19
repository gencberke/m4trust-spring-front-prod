package com.m4trust.coreapi.contractintelligence;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class CommittedEventSchemaValidatorTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final CommittedEventSchemaValidator validator =
            new CommittedEventSchemaValidator(mapper);

    @Test
    void validatesCommittedCompletedShapeAndRejectsExactEnvelopeViolations() throws Exception {
        Map<String, Object> event = completed();
        assertDoesNotThrow(() -> validator.validateCompleted(mapper.valueToTree(event)));

        for (String field : List.of("schemaVersion", "jobType", "eventType")) {
            Map<String, Object> invalid = copy(event);
            invalid.put(field, "WRONG");
            assertInvalidCompleted(invalid);
        }

        Map<String, Object> invalidUuid = copy(event);
        invalidUuid.put("eventId", "not-a-uuid");
        assertInvalidCompleted(invalidUuid);

        Map<String, Object> invalidHash = copy(event);
        document(invalidHash).put("contentSha256", "x");
        assertInvalidCompleted(invalidHash);
    }

    @Test
    void enforcesUtcClosedObjectsStructuredValueAndLegalBasisPolicy() throws Exception {
        Map<String, Object> offsetTimestamp = copy(completed());
        offsetTimestamp.put("occurredAt", "2026-07-19T03:00:00+03:00");
        assertInvalidCompleted(offsetTimestamp);

        Map<String, Object> additionalRuleField = copy(completed());
        rule(additionalRuleField).put("providerGuess", "must-not-cross-boundary");
        assertInvalidCompleted(additionalRuleField);

        Map<String, Object> invalidStructuredUnion = copy(completed());
        structuredValue(invalidStructuredUnion).put("amountMinor", 100);
        assertInvalidCompleted(invalidStructuredUnion);

        Map<String, Object> invalidLegalBasis = copy(completed());
        legalBasis(invalidLegalBasis).put("source", "unknown-law");
        assertInvalidCompleted(invalidLegalBasis);

        Map<String, Object> additionalLegalBasisField = copy(completed());
        legalBasis(additionalLegalBasisField).put("excerpt", "not in contract");
        assertInvalidCompleted(additionalLegalBasisField);
    }

    @Test
    void validatesFailedFixtureAndEnforcesClosedDiscriminatedErrorPolicy() throws Exception {
        Map<String, Object> event = failed();
        assertDoesNotThrow(() -> validator.validateFailed(mapper.valueToTree(event)));

        Map<String, Object> categoryCodeMismatch = copy(event);
        error(categoryCodeMismatch).put("category", "INVALID_INPUT");
        assertInvalidFailed(categoryCodeMismatch);

        Map<String, Object> retryMismatch = copy(event);
        error(retryMismatch).put("retryRecommended", false);
        assertInvalidFailed(retryMismatch);

        Map<String, Object> additionalErrorField = copy(event);
        error(additionalErrorField).put("providerMessage", "must-not-cross-boundary");
        assertInvalidFailed(additionalErrorField);
    }

    private void assertInvalidCompleted(Map<String, Object> event) {
        assertThrows(CommittedEventSchemaValidator.ContractViolationException.class,
                () -> validator.validateCompleted(mapper.valueToTree(event)));
    }

    private void assertInvalidFailed(Map<String, Object> event) {
        assertThrows(CommittedEventSchemaValidator.ContractViolationException.class,
                () -> validator.validateFailed(mapper.valueToTree(event)));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> copy(Map<String, Object> source) throws Exception {
        return mapper.readValue(mapper.writeValueAsString(source), LinkedHashMap.class);
    }

    private Map<String, Object> completed() {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("detectedMediaType", "application/pdf");
        document.put("detectedLanguage", "tr");
        document.put("pageCount", 1);
        document.put("textExtractionMethod", "DIGITAL_PDF");
        document.put("contentSha256", "a".repeat(64));

        Map<String, Object> rule = new LinkedHashMap<>();
        rule.put("ruleReference", "rule-1");
        rule.put("category", "OTHER");
        rule.put("title", "Rule");
        rule.put("description", "Canonical rule");
        rule.put("structuredValue", new LinkedHashMap<>(Map.of("type", "TEXT", "value", "v")));
        rule.put("confidence", 0.5);
        rule.put("sourceReferences", List.of());
        rule.put("legalBasis", new LinkedHashMap<>(
                Map.of("source", "tbk-6098", "articleNo", "1")));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("document", document);
        result.put("parties", List.of());
        result.put("rules", List.of(rule));
        result.put("deliveryRequirements", List.of());
        result.put("summary", Map.of("requiresManualReview", false, "reviewReasons", List.of()));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("result", result);
        payload.put("technicalMetadata", Map.of("pipelineVersion", "1", "durationMs", 0));
        payload.put("warnings", List.of());
        return envelope("ai.job.completed.v1", payload);
    }

    private Map<String, Object> failed() {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("category", "RETRYABLE_TECHNICAL");
        error.put("code", "MODEL_PROVIDER_TIMEOUT");
        error.put("message", "Safe failure summary");
        error.put("retryRecommended", true);
        error.put("details", null);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("error", error);
        payload.put("attempt", Map.of("attemptNumber", 1, "maxAttempts", 3));
        payload.put("technicalMetadata", Map.of("pipelineVersion", "1", "durationMs", 0));
        return envelope("ai.job.failed.v1", payload);
    }

    private Map<String, Object> envelope(String eventType, Object payload) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("eventId", UUID.randomUUID().toString());
        event.put("eventType", eventType);
        event.put("schemaVersion", "1.0.0");
        event.put("occurredAt", "2026-07-19T00:00:00Z");
        event.put("correlationId", UUID.randomUUID().toString());
        event.put("causationId", null);
        event.put("jobId", UUID.randomUUID().toString());
        event.put("jobType", "DOCUMENT_EXTRACTION");
        event.put("tenantId", UUID.randomUUID().toString());
        event.put("transactionId", UUID.randomUUID().toString());
        event.put("subjectId", UUID.randomUUID().toString());
        event.put("idempotencyKey", "key");
        event.put("producer", Map.of("service", "worker", "version", "1.0.0"));
        event.put("payload", payload);
        return event;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> document(Map<String, Object> event) {
        return (Map<String, Object>) result(event).get("document");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> rule(Map<String, Object> event) {
        return (Map<String, Object>) ((List<Object>) result(event).get("rules")).getFirst();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> structuredValue(Map<String, Object> event) {
        return (Map<String, Object>) rule(event).get("structuredValue");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> legalBasis(Map<String, Object> event) {
        return (Map<String, Object>) rule(event).get("legalBasis");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> result(Map<String, Object> event) {
        return (Map<String, Object>) ((Map<String, Object>) event.get("payload")).get("result");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> error(Map<String, Object> event) {
        return (Map<String, Object>) ((Map<String, Object>) event.get("payload")).get("error");
    }
}
