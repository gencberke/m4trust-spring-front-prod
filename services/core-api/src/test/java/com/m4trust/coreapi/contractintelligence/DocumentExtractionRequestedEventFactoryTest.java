package com.m4trust.coreapi.contractintelligence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;

import com.m4trust.coreapi.document.DocumentAnalysisInputPort;
import com.m4trust.coreapi.document.DocumentObjectStorage;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class DocumentExtractionRequestedEventFactoryTest {

    @Test
    void createsTheCommittedRequestedEnvelopeWithPinnedInputAndUtcTimes() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        UUID eventId = UUID.fromString("77777777-7777-4777-8777-777777777777");
        UUID jobId = UUID.fromString("88888888-8888-4888-8888-888888888888");
        UUID tenantId = UUID.fromString("44444444-4444-4444-8444-444444444444");
        UUID dealId = UUID.fromString("99999999-9999-4999-8999-999999999999");
        UUID documentId = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
        UUID correlationId = UUID.fromString("22222222-2222-4222-8222-222222222222");
        UUID idempotencyKey = UUID.fromString("11111111-1111-4111-8111-111111111111");
        Instant occurredAt = Instant.parse("2026-07-13T18:01:00Z");
        Instant expiresAt = Instant.parse("2026-07-13T18:16:00Z");

        String json = new DocumentExtractionRequestedEventFactory(mapper, "1.0.0").create(
                eventId, jobId, tenantId, dealId, correlationId, idempotencyKey,
                new DocumentAnalysisInputPort.Input(documentId, dealId,
                        "supply-agreement-tr.pdf", "application/pdf", 248193,
                        "b".repeat(64), "private/key", "immutable-version"),
                new DocumentObjectStorage.DirectDownload(
                        URI.create("https://objects.example/contracts/supply-agreement-tr.pdf?versionId=immutable-version"),
                        expiresAt), occurredAt);

        JsonNode event = mapper.readTree(json);
        assertEquals(eventId.toString(), event.get("eventId").asText());
        assertEquals("ai.job.requested.v1", event.get("eventType").asText());
        assertEquals("1.0.0", event.get("schemaVersion").asText());
        assertEquals("2026-07-13T18:01:00Z", event.get("occurredAt").asText());
        assertEquals("m4trust-core-api", event.get("producer").get("service").asText());
        assertEquals("1.0.0", event.get("producer").get("version").asText());
        assertEquals(tenantId.toString(), event.get("tenantId").asText());
        assertEquals(dealId.toString(), event.get("transactionId").asText());
        assertEquals(documentId.toString(), event.get("subjectId").asText());
        assertEquals(idempotencyKey.toString(), event.get("idempotencyKey").asText());
        assertEquals("https://objects.example/contracts/supply-agreement-tr.pdf?versionId=immutable-version",
                event.get("payload").get("input").get("download").get("url").asText());
        assertEquals("b".repeat(64), event.get("payload").get("input").get("sha256").asText());
        assertEquals("2026-07-13T18:16:00Z",
                event.get("payload").get("input").get("download").get("expiresAt").asText());
        assertEquals("2026-07-13T18:16:00Z",
                event.get("payload").get("deadlineAt").asText());
        assertFalse(json.contains("objectKey"));
        assertFalse(json.contains("objectVersion"));
        assertFalse(json.contains("provider"));
        assertFalse(json.contains("secret"));
    }
}
