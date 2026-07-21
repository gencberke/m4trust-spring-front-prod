package com.m4trust.coreapi.integration.messaging;

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
    void validatesCommittedDocumentCompletedShapeAndRejectsExactEnvelopeViolations() throws Exception {
        Map<String, Object> event = documentCompleted();
        assertDoesNotThrow(() -> validator.validateDocumentCompleted(mapper.valueToTree(event)));

        for (String field : List.of("schemaVersion", "jobType", "eventType")) {
            Map<String, Object> invalid = copy(event);
            invalid.put(field, "WRONG");
            assertInvalidDocumentCompleted(invalid);
        }
    }

    @Test
    void validatesCommittedVideoCompletedAndFailedFixtures() throws Exception {
        assertDoesNotThrow(() -> validator.validateVideoCompleted(mapper.valueToTree(videoCompleted())));
        assertDoesNotThrow(() -> validator.validateVideoFailed(mapper.valueToTree(videoFailed())));

        Map<String, Object> invalidJobType = copy(videoCompleted());
        invalidJobType.put("jobType", "DOCUMENT_EXTRACTION");
        assertThrows(CommittedEventSchemaValidator.ContractViolationException.class,
                () -> validator.validateVideoCompleted(mapper.valueToTree(invalidJobType)));
    }

    @Test
    void validatesFailedFixtureAndEnforcesClosedDiscriminatedErrorPolicy() throws Exception {
        Map<String, Object> event = documentFailed();
        assertDoesNotThrow(() -> validator.validateDocumentFailed(mapper.valueToTree(event)));

        Map<String, Object> categoryCodeMismatch = copy(event);
        error(categoryCodeMismatch).put("category", "INVALID_INPUT");
        assertInvalidDocumentFailed(categoryCodeMismatch);
    }

    private void assertInvalidDocumentCompleted(Map<String, Object> event) {
        assertThrows(CommittedEventSchemaValidator.ContractViolationException.class,
                () -> validator.validateDocumentCompleted(mapper.valueToTree(event)));
    }

    private void assertInvalidDocumentFailed(Map<String, Object> event) {
        assertThrows(CommittedEventSchemaValidator.ContractViolationException.class,
                () -> validator.validateDocumentFailed(mapper.valueToTree(event)));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> copy(Map<String, Object> source) throws Exception {
        return mapper.readValue(mapper.writeValueAsString(source), LinkedHashMap.class);
    }

    private Map<String, Object> documentCompleted() {
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
        rule.put("legalBasis", new LinkedHashMap<>(Map.of("source", "tbk-6098", "articleNo", "1")));

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
        return envelope("DOCUMENT_EXTRACTION", "ai.job.completed.v1", payload);
    }

    private Map<String, Object> videoCompleted() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("durationMs", 92000);
        result.put("observations", List.of(Map.of(
                "observationReference", "observation-1",
                "type", "OBJECT_COUNT",
                "label", "sealed_box",
                "observedValue", 4,
                "confidence", 0.93,
                "timeRange", Map.of("startMs", 4000, "endMs", 24000))));
        result.put("anomalies", List.of());
        result.put("summary", Map.of("advisoryOutcome", "NO_ISSUE_DETECTED", "reviewReasons", List.of()));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("result", result);
        payload.put("technicalMetadata", technicalMetadata());
        payload.put("warnings", List.of());
        return envelope("VIDEO_ANALYSIS", "ai.job.completed.v1", payload);
    }

    private Map<String, Object> technicalMetadata() {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("pipelineVersion", "video-pipeline-1.0.0");
        metadata.put("modelProvider", "internal-model-service");
        metadata.put("modelFamily", "delivery-evidence");
        metadata.put("modelVersion", "2026-07");
        metadata.put("promptVersion", null);
        metadata.put("retrievalVersion", null);
        metadata.put("parserVersion", "video-parser-1.0.0");
        metadata.put("privacyVersion", "default-1");
        metadata.put("durationMs", 11400);
        return metadata;
    }

    private Map<String, Object> videoFailed() {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("category", "RETRYABLE_TECHNICAL");
        error.put("code", "OBJECT_STORAGE_TEMPORARILY_UNAVAILABLE");
        error.put("message", "The source media could not be downloaded temporarily.");
        error.put("retryRecommended", true);
        error.put("details", Map.of("dependency", "object-storage", "reason", "temporary unavailability",
                "retryAfterMs", 3000));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("error", error);
        payload.put("attempt", Map.of("attemptNumber", 2, "maxAttempts", 3));
        payload.put("technicalMetadata", failedTechnicalMetadata());
        return envelope("VIDEO_ANALYSIS", "ai.job.failed.v1", payload);
    }

    private Map<String, Object> failedTechnicalMetadata() {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("pipelineVersion", "video-pipeline-1.0.0");
        metadata.put("modelProvider", null);
        metadata.put("modelFamily", null);
        metadata.put("modelVersion", null);
        metadata.put("promptVersion", null);
        metadata.put("retrievalVersion", null);
        metadata.put("parserVersion", "video-parser-1.0.0");
        metadata.put("privacyVersion", "default-1");
        metadata.put("durationMs", 3000);
        return metadata;
    }

    private Map<String, Object> documentFailed() {
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
        return envelope("DOCUMENT_EXTRACTION", "ai.job.failed.v1", payload);
    }

    private Map<String, Object> envelope(String jobType, String eventType, Object payload) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("eventId", UUID.randomUUID().toString());
        event.put("eventType", eventType);
        event.put("schemaVersion", "1.0.0");
        event.put("occurredAt", "2026-07-19T00:00:00Z");
        event.put("correlationId", UUID.randomUUID().toString());
        event.put("causationId", null);
        event.put("jobId", UUID.randomUUID().toString());
        event.put("jobType", jobType);
        event.put("tenantId", UUID.randomUUID().toString());
        event.put("transactionId", UUID.randomUUID().toString());
        event.put("subjectId", UUID.randomUUID().toString());
        event.put("idempotencyKey", "key");
        event.put("producer", Map.of("service", "worker", "version", "1.0.0"));
        event.put("payload", payload);
        return event;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> error(Map<String, Object> event) {
        return (Map<String, Object>) ((Map<String, Object>) event.get("payload")).get("error");
    }
}
