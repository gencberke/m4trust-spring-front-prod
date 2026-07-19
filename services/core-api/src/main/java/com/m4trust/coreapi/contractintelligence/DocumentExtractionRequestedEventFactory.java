package com.m4trust.coreapi.contractintelligence;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.m4trust.coreapi.document.DocumentAnalysisInputPort;
import com.m4trust.coreapi.document.DocumentObjectStorage;
import tools.jackson.databind.ObjectMapper;

/** Creates the contract-owned requested-event envelope without leaking storage bytes or secrets. */
final class DocumentExtractionRequestedEventFactory {

    static final String EVENT_TYPE = "ai.job.requested.v1";
    static final String SCHEMA_VERSION = "1.0.0";
    static final String PRODUCER_SERVICE = "m4trust-core-api";

    private final ObjectMapper objectMapper;
    private final String producerVersion;

    DocumentExtractionRequestedEventFactory(ObjectMapper objectMapper, String producerVersion) {
        this.objectMapper = objectMapper;
        this.producerVersion = producerVersion;
    }

    String create(UUID eventId, UUID jobId, UUID tenantId, UUID dealId,
            UUID correlationId, UUID idempotencyKey,
            DocumentAnalysisInputPort.Input document,
            DocumentObjectStorage.DirectDownload download, Instant occurredAt) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("eventId", eventId);
        event.put("eventType", EVENT_TYPE);
        event.put("schemaVersion", SCHEMA_VERSION);
        event.put("occurredAt", utc(occurredAt));
        event.put("correlationId", correlationId);
        event.put("causationId", null);
        event.put("jobId", jobId);
        event.put("jobType", "DOCUMENT_EXTRACTION");
        event.put("tenantId", tenantId);
        event.put("transactionId", dealId);
        event.put("subjectId", document.id());
        event.put("idempotencyKey", idempotencyKey.toString());
        event.put("producer", Map.of("service", PRODUCER_SERVICE, "version", producerVersion));
        event.put("payload", payload(document, download));
        try {
            return objectMapper.writeValueAsString(event);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not serialize document extraction request", exception);
        }
    }

    private static Map<String, Object> payload(DocumentAnalysisInputPort.Input document,
            DocumentObjectStorage.DirectDownload download) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("documentId", document.id());
        input.put("fileName", document.fileName());
        input.put("mediaType", document.mediaType());
        input.put("sizeBytes", document.sizeBytes());
        input.put("sha256", document.sha256());
        input.put("download", Map.of("url", download.url().toString(),
                "expiresAt", utc(download.expiresAt())));

        Map<String, Object> processing = new LinkedHashMap<>();
        processing.put("languageHints", List.of());
        processing.put("documentCategory", "B2B_CONTRACT");
        processing.put("requestedOutputSchema", "m4trust.document-extraction-result");
        processing.put("requestedOutputSchemaVersion", SCHEMA_VERSION);
        processing.put("privacyProfile", "DEFAULT");
        processing.put("retrievalProfile", "M4TRUST_LEGAL_DEFAULT");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("input", input);
        payload.put("processing", processing);
        payload.put("deadlineAt", utc(download.expiresAt()));
        return payload;
    }

    private static String utc(Instant value) {
        return DateTimeFormatter.ISO_INSTANT.format(value);
    }
}
