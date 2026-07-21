package com.m4trust.coreapi.fulfillment;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import tools.jackson.databind.ObjectMapper;

/** Creates the contract-owned video analysis requested-event envelope. */
final class VideoAnalysisRequestedEventFactory {

    static final String EVENT_TYPE = "ai.job.requested.v1";
    static final String SCHEMA_VERSION = "1.0.0";
    static final String PRODUCER_SERVICE = "m4trust-core-api";
    static final String ROUTING_KEY = "ai.video-analysis.requested.v1";

    private final ObjectMapper objectMapper;
    private final String producerVersion;

    VideoAnalysisRequestedEventFactory(ObjectMapper objectMapper, String producerVersion) {
        this.objectMapper = objectMapper;
        this.producerVersion = producerVersion;
    }

    String create(UUID eventId, UUID jobId, UUID tenantId, UUID dealId, UUID correlationId,
            UUID idempotencyKey, VideoAnalysisEvidenceInputPort.VerifiedSnapshot evidence,
            FulfillmentObjectStorage.DirectDownload download, Instant occurredAt) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("eventId", eventId);
        event.put("eventType", EVENT_TYPE);
        event.put("schemaVersion", SCHEMA_VERSION);
        event.put("occurredAt", utc(occurredAt));
        event.put("correlationId", correlationId);
        event.put("causationId", null);
        event.put("jobId", jobId);
        event.put("jobType", "VIDEO_ANALYSIS");
        event.put("tenantId", tenantId);
        event.put("transactionId", dealId);
        event.put("subjectId", evidence.evidenceSubmissionId());
        event.put("idempotencyKey", idempotencyKey.toString());
        event.put("producer", Map.of("service", PRODUCER_SERVICE, "version", producerVersion));
        event.put("payload", payload(evidence, download));
        try {
            return objectMapper.writeValueAsString(event);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not serialize video analysis request", exception);
        }
    }

    private static Map<String, Object> payload(VideoAnalysisEvidenceInputPort.VerifiedSnapshot evidence,
            FulfillmentObjectStorage.DirectDownload download) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("videoId", evidence.evidenceSubmissionId());
        input.put("fileName", evidence.fileName());
        input.put("mediaType", evidence.mediaType().value());
        input.put("sizeBytes", evidence.verifiedSizeBytes());
        input.put("sha256", evidence.verifiedSha256());
        input.put("download", Map.of("url", download.url().toString(),
                "expiresAt", utc(download.expiresAt())));

        Map<String, Object> processing = new LinkedHashMap<>();
        processing.put("analysisProfile", "DELIVERY_EVIDENCE_DEFAULT");
        processing.put("expectedObjects", List.of());
        processing.put("requestedOutputSchema", "m4trust.video-analysis-result");
        processing.put("requestedOutputSchemaVersion", SCHEMA_VERSION);

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
