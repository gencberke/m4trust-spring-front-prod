package com.m4trust.coreapi.contractintelligence;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import com.m4trust.coreapi.audit.AuditAppendPort;
import com.m4trust.coreapi.audit.AuditRecord;
import com.m4trust.coreapi.integration.messaging.AiTerminalResultHandler;
import com.m4trust.coreapi.integration.messaging.IntegrationViolation;
import com.m4trust.coreapi.integration.messaging.TransactionalInbox;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Applies document-extraction terminal events after router-owned schema validation. */
@Service
final class DocumentExtractionResultHandler implements AiTerminalResultHandler {

    private static final String JOB_TYPE = "DOCUMENT_EXTRACTION";

    private final ObjectMapper mapper;
    private final TransactionalInbox inbox;
    private final AnalysisRepository repository;
    private final AuditAppendPort audit;
    private final TransactionTemplate transactions;
    private final Clock clock;

    DocumentExtractionResultHandler(ObjectMapper mapper, TransactionalInbox inbox,
            AnalysisRepository repository, AuditAppendPort audit, TransactionTemplate transactions,
            Clock clock) {
        this.mapper = mapper;
        this.inbox = inbox;
        this.repository = repository;
        this.audit = audit;
        this.transactions = transactions;
        this.clock = clock;
    }

    @Override
    public String jobType() {
        return JOB_TYPE;
    }

    @Override
    public void handle(JsonNode event, String eventType) {
        transactions.executeWithoutResult(status -> apply(event, eventType));
    }

    private void apply(JsonNode event, String eventType) {
        UUID eventId = uuid(event, "eventId");
        if (!inbox.recordIfNew(eventId, eventType)) {
            return;
        }
        UUID jobId = uuid(event, "jobId");
        AnalysisRepository.AnalysisJob job = repository.findByIdForUpdate(jobId)
                .orElseThrow(IntegrationViolation::new);
        requireIdentity(event, job);
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        if (job.status() == AnalysisJobStatus.SUPERSEDED || terminal(job.status())) {
            appendAudit(job, event, "AI_ANALYSIS_TERMINAL_EVENT_IGNORED", now);
            return;
        }
        if ("ai.job.completed.v1".equals(eventType)) {
            complete(event, job, now);
        } else {
            fail(event, job, now);
        }
    }

    private void complete(JsonNode event, AnalysisRepository.AnalysisJob job, Instant now) {
        JsonNode payload = event.get("payload");
        String contentHash = payload.path("result").path("document").path("contentSha256").asString();
        if (!job.inputSha256().equalsIgnoreCase(contentHash)) {
            throw new IntegrationViolation();
        }
        try {
            repository.insertResult(UUID.randomUUID(), job.id(), event.get("schemaVersion").asString(),
                    mapper.writeValueAsString(payload.get("result")), now);
        } catch (Exception exception) {
            throw new IllegalStateException("Canonical result cannot be persisted", exception);
        }
        repository.complete(job, job.processingStartedAt() == null ? now : job.processingStartedAt(), now);
        appendAudit(job, event, "AI_DOCUMENT_EXTRACTION_COMPLETED", now);
    }

    private void fail(JsonNode event, AnalysisRepository.AnalysisJob job, Instant now) {
        JsonNode attempt = event.path("payload").path("attempt");
        if (attempt.path("attemptNumber").asInt() > attempt.path("maxAttempts").asInt()) {
            throw new IntegrationViolation();
        }
        JsonNode error = event.path("payload").path("error");
        repository.fail(job, now, error.path("code").asString(), error.path("retryRecommended").asBoolean());
        appendAudit(job, event, "AI_DOCUMENT_EXTRACTION_FAILED", now);
    }

    private void requireIdentity(JsonNode event, AnalysisRepository.AnalysisJob job) {
        if (!uuid(event, "tenantId").equals(job.tenantId())
                || !uuid(event, "transactionId").equals(job.dealId())
                || !uuid(event, "subjectId").equals(job.documentId())) {
            throw new IntegrationViolation();
        }
    }

    private void appendAudit(AnalysisRepository.AnalysisJob job, JsonNode event, String action, Instant now) {
        audit.append(new AuditRecord(UUID.randomUUID(), job.tenantId(), null, null, "ANALYSIS_JOB", job.id(),
                action, uuid(event, "correlationId"), uuid(event, "eventId"), now));
    }

    private static boolean terminal(AnalysisJobStatus status) {
        return status == AnalysisJobStatus.REVIEW_REQUIRED || status == AnalysisJobStatus.ACCEPTED
                || status == AnalysisJobStatus.FAILED;
    }

    private static UUID uuid(JsonNode event, String name) {
        return UUID.fromString(event.get(name).asString());
    }
}
