package com.m4trust.coreapi.fulfillment;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import com.m4trust.coreapi.audit.AuditAppendPort;
import com.m4trust.coreapi.audit.AuditRecord;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Applies video-analysis terminal events without mutating evidence or Deal state. */
@Service
class VideoAnalysisResultService implements VideoAnalysisTerminalEventPort {

    private static final String AUDIT_SUBJECT = "VIDEO_ANALYSIS_JOB";

    private final ObjectMapper mapper;
    private final VideoAnalysisTerminalInboxPort inbox;
    private final VideoAnalysisRepository repository;
    private final VideoAnalysisEvidenceInputPort evidenceInputs;
    private final AuditAppendPort audit;
    private final TransactionTemplate transactions;
    private final Clock clock;

    VideoAnalysisResultService(ObjectMapper mapper, VideoAnalysisTerminalInboxPort inbox,
            VideoAnalysisRepository repository, VideoAnalysisEvidenceInputPort evidenceInputs,
            AuditAppendPort audit, TransactionTemplate transactions, Clock clock) {
        this.mapper = mapper;
        this.inbox = inbox;
        this.repository = repository;
        this.evidenceInputs = evidenceInputs;
        this.audit = audit;
        this.transactions = transactions;
        this.clock = clock;
    }

    @Override
    public void apply(JsonNode event, String eventType) {
        transactions.executeWithoutResult(status -> applyInTransaction(event, eventType));
    }

    private void applyInTransaction(JsonNode event, String eventType) {
        UUID eventId = uuid(event, "eventId");
        if (!inbox.recordIfNew(eventId, eventType)) {
            return;
        }
        UUID jobId = uuid(event, "jobId");
        VideoAnalysisRepository.VideoAnalysisJobRecord job = repository.findByIdForUpdate(jobId)
                .orElseThrow(VideoAnalysisTerminalViolation::new);
        requireIdentity(event, job);
        requireImmutableInputSnapshot(job);
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        if (terminal(job.status())) {
            appendAudit(job, event, "AI_ANALYSIS_TERMINAL_EVENT_IGNORED", now);
            return;
        }
        if (job.status() != VideoAnalysisJobStatus.QUEUED) {
            throw new VideoAnalysisTerminalViolation();
        }
        if ("ai.job.completed.v1".equals(eventType)) {
            complete(event, job, now);
        } else {
            fail(event, job, now);
        }
    }

    private void complete(JsonNode event, VideoAnalysisRepository.VideoAnalysisJobRecord job, Instant now) {
        JsonNode payload = event.get("payload");
        validateVideoSemantics(payload);
        try {
            repository.insertResult(UUID.randomUUID(), job.id(), event.get("schemaVersion").asString(),
                    mapper.writeValueAsString(payload), now);
        } catch (DuplicateKeyException exception) {
            throw new VideoAnalysisTerminalViolation();
        } catch (Exception exception) {
            throw new IllegalStateException("Canonical result cannot be persisted", exception);
        }
        repository.complete(job, now);
        appendAudit(job, event, "AI_VIDEO_ANALYSIS_COMPLETED", now);
    }

    private void fail(JsonNode event, VideoAnalysisRepository.VideoAnalysisJobRecord job, Instant now) {
        JsonNode attempt = event.path("payload").path("attempt");
        if (attempt.path("attemptNumber").asInt() > attempt.path("maxAttempts").asInt()) {
            throw new VideoAnalysisTerminalViolation();
        }
        JsonNode error = event.path("payload").path("error");
        repository.fail(job, now, error.path("code").asString(), error.path("retryRecommended").asBoolean());
        appendAudit(job, event, "AI_VIDEO_ANALYSIS_FAILED", now);
    }

    private void requireIdentity(JsonNode event, VideoAnalysisRepository.VideoAnalysisJobRecord job) {
        if (!uuid(event, "tenantId").equals(job.tenantId())
                || !uuid(event, "transactionId").equals(job.dealId())
                || !uuid(event, "subjectId").equals(job.evidenceSubmissionId())) {
            throw new VideoAnalysisTerminalViolation();
        }
    }

    private void requireImmutableInputSnapshot(VideoAnalysisRepository.VideoAnalysisJobRecord job) {
        VideoAnalysisEvidenceInputPort.VerifiedSnapshot snapshot = evidenceInputs
                .findVerifiedSnapshot(job.evidenceSubmissionId())
                .orElseThrow(VideoAnalysisTerminalViolation::new);
        if (!snapshot.objectVersion().equals(job.objectVersion())
                || !snapshot.verifiedSha256().equalsIgnoreCase(job.inputSha256())
                || snapshot.verifiedSizeBytes() != job.inputSizeBytes()) {
            throw new VideoAnalysisTerminalViolation();
        }
    }

    private void validateVideoSemantics(JsonNode payload) {
        JsonNode result = payload.get("result");
        long durationMs = result.path("durationMs").asLong();
        Set<String> observationReferences = new HashSet<>();
        for (JsonNode observation : result.path("observations")) {
            if (!observationReferences.add(observation.path("observationReference").asString())) {
                throw new VideoAnalysisTerminalViolation();
            }
            validateTimeRange(observation.path("timeRange"), durationMs);
        }
        Set<String> anomalyReferences = new HashSet<>();
        for (JsonNode anomaly : result.path("anomalies")) {
            if (!anomalyReferences.add(anomaly.path("anomalyReference").asString())) {
                throw new VideoAnalysisTerminalViolation();
            }
            validateTimeRange(anomaly.path("timeRange"), durationMs);
        }
    }

    private static void validateTimeRange(JsonNode range, long durationMs) {
        long startMs = range.path("startMs").asLong();
        long endMs = range.path("endMs").asLong();
        if (endMs <= startMs || endMs > durationMs) {
            throw new VideoAnalysisTerminalViolation();
        }
    }

    private void appendAudit(VideoAnalysisRepository.VideoAnalysisJobRecord job, JsonNode event,
            String action, Instant now) {
        audit.append(new AuditRecord(UUID.randomUUID(), job.tenantId(), null, null, AUDIT_SUBJECT, job.id(),
                action, uuid(event, "correlationId"), uuid(event, "eventId"), now));
    }

    private static boolean terminal(VideoAnalysisJobStatus status) {
        return status == VideoAnalysisJobStatus.RESULT_AVAILABLE
                || status == VideoAnalysisJobStatus.FAILED;
    }

    private static UUID uuid(JsonNode event, String name) {
        return UUID.fromString(event.get(name).asString());
    }
}
