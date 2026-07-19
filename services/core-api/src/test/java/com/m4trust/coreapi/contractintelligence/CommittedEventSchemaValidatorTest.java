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
    private final CommittedEventSchemaValidator validator = new CommittedEventSchemaValidator(mapper);

    @Test
    void validatesCommittedCompletedShapeAndRejectsExactContractViolations() throws Exception {
        Map<String, Object> event = completed();
        assertDoesNotThrow(() -> validator.validateCompleted(mapper.valueToTree(event)));
        for (String field : List.of("schemaVersion", "jobType", "eventType")) {
            Map<String, Object> invalid = new LinkedHashMap<>(event);
            invalid.put(field, "WRONG");
            assertThrows(CommittedEventSchemaValidator.ContractViolationException.class,
                    () -> validator.validateCompleted(mapper.valueToTree(invalid)));
        }
        Map<String, Object> invalidUuid = new LinkedHashMap<>(event); invalidUuid.put("eventId", "not-a-uuid");
        assertThrows(CommittedEventSchemaValidator.ContractViolationException.class,
                () -> validator.validateCompleted(mapper.valueToTree(invalidUuid)));
        Map<String, Object> invalidHash = completed();
        ((Map<String, Object>) ((Map<String, Object>) invalidHash.get("payload")).get("result"))
                .put("document", Map.of("detectedMediaType", "application/pdf", "detectedLanguage", "tr",
                        "pageCount", 1, "textExtractionMethod", "DIGITAL_PDF", "contentSha256", "x"));
        assertThrows(CommittedEventSchemaValidator.ContractViolationException.class,
                () -> validator.validateCompleted(mapper.valueToTree(invalidHash)));
    }

    private Map<String, Object> completed() {
        String id = UUID.randomUUID().toString();
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("detectedMediaType", "application/pdf"); document.put("detectedLanguage", "tr");
        document.put("pageCount", 1); document.put("textExtractionMethod", "DIGITAL_PDF"); document.put("contentSha256", "a".repeat(64));
        Map<String, Object> result = new LinkedHashMap<>(); result.put("document", document); result.put("parties", List.of());
        result.put("rules", List.of()); result.put("deliveryRequirements", List.of());
        result.put("summary", Map.of("requiresManualReview", false, "reviewReasons", List.of()));
        Map<String, Object> payload = new LinkedHashMap<>(); payload.put("result", result);
        payload.put("technicalMetadata", Map.of("pipelineVersion", "1", "durationMs", 0)); payload.put("warnings", List.of());
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("eventId", id); event.put("eventType", "ai.job.completed.v1"); event.put("schemaVersion", "1.0.0");
        event.put("occurredAt", "2026-07-19T00:00:00Z"); event.put("correlationId", UUID.randomUUID().toString()); event.put("causationId", null);
        event.put("jobId", UUID.randomUUID().toString()); event.put("jobType", "DOCUMENT_EXTRACTION"); event.put("tenantId", UUID.randomUUID().toString());
        event.put("transactionId", UUID.randomUUID().toString()); event.put("subjectId", UUID.randomUUID().toString()); event.put("idempotencyKey", "key");
        event.put("producer", Map.of("service", "worker", "version", "1.0.0")); event.put("payload", payload); return event;
    }
}
