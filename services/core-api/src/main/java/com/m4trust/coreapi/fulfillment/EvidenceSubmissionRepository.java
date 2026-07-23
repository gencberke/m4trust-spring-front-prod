package com.m4trust.coreapi.fulfillment;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class EvidenceSubmissionRepository {

    private final JdbcTemplate jdbcTemplate;

    EvidenceSubmissionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    void insert(EvidenceSubmission.EvidenceSubmissionRecord submission) {
        jdbcTemplate.update("""
                INSERT INTO fulfillment_evidence_submission (
                    id, deal_id, milestone_id, fulfillment_id, evidence_type,
                    media_type, file_name, status, object_key, object_version,
                    client_size_bytes, client_sha256, verified_size_bytes,
                    verified_sha256, upload_expires_at, created_at, submitted_at,
                    accepted_at, rejected_at, rejection_reason, version
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, submission.id(), submission.dealId(), submission.milestoneId(),
                submission.fulfillmentId(), submission.evidenceType().name(),
                submission.mediaType().value(), submission.fileName(), submission.status().name(),
                submission.objectKey(), submission.objectVersion(), submission.clientSizeBytes(),
                submission.clientSha256(), submission.verifiedSizeBytes(),
                submission.verifiedSha256(), Timestamp.from(submission.uploadExpiresAt()),
                Timestamp.from(submission.createdAt()), timestamp(submission.submittedAt()),
                timestamp(submission.acceptedAt()), timestamp(submission.rejectedAt()),
                submission.rejectionReason(), submission.version());
    }

    Optional<EvidenceSubmission.EvidenceSubmissionRecord> findById(UUID submissionId) {
        return jdbcTemplate.query("SELECT * FROM fulfillment_evidence_submission WHERE id = ?",
                this::mapSubmission, submissionId).stream().findFirst();
    }

    Optional<EvidenceSubmission.EvidenceSubmissionRecord> findByIdForUpdate(UUID submissionId) {
        return jdbcTemplate.query("SELECT * FROM fulfillment_evidence_submission WHERE id = ? FOR UPDATE",
                this::mapSubmission, submissionId).stream().findFirst();
    }

    List<EvidenceSubmission.EvidenceSubmissionRecord> findByMilestoneId(UUID milestoneId) {
        return jdbcTemplate.query("""
                SELECT * FROM fulfillment_evidence_submission
                WHERE milestone_id = ?
                ORDER BY created_at DESC, id DESC
                """, this::mapSubmission, milestoneId);
    }

    List<EvidenceSubmission.EvidenceSubmissionRecord> findByFulfillmentId(UUID fulfillmentId) {
        return jdbcTemplate.query("""
                SELECT * FROM fulfillment_evidence_submission
                WHERE fulfillment_id = ?
                ORDER BY created_at DESC, id DESC
                """, this::mapSubmission, fulfillmentId);
    }

    boolean existsByFulfillmentId(UUID fulfillmentId) {
        Boolean exists = jdbcTemplate.queryForObject("""
                SELECT EXISTS(
                    SELECT 1 FROM fulfillment_evidence_submission WHERE fulfillment_id = ?
                )
                """, Boolean.class, fulfillmentId);
        return Boolean.TRUE.equals(exists);
    }

    Optional<EvidenceSubmission.EvidenceSubmissionRecord> findCurrentSubmittedByMilestoneId(UUID milestoneId) {
        return jdbcTemplate.query("""
                SELECT * FROM fulfillment_evidence_submission
                WHERE milestone_id = ? AND status = 'SUBMITTED'
                """, this::mapSubmission, milestoneId).stream().findFirst();
    }

    Optional<EvidenceSubmission.EvidenceSubmissionRecord> findCurrentSubmittedByFulfillmentId(UUID fulfillmentId) {
        return jdbcTemplate.query("""
                SELECT * FROM fulfillment_evidence_submission
                WHERE fulfillment_id = ? AND status = 'SUBMITTED'
                ORDER BY created_at DESC, id DESC
                LIMIT 1
                """, this::mapSubmission, fulfillmentId).stream().findFirst();
    }

    List<EvidenceSubmission.EvidenceSubmissionRecord> findFinalizedByDealIdOrderById(UUID dealId) {
        return jdbcTemplate.query("""
                SELECT * FROM fulfillment_evidence_submission
                WHERE deal_id = ? AND status IN ('SUBMITTED', 'ACCEPTED', 'REJECTED')
                ORDER BY id ASC
                """, this::mapSubmission, dealId);
    }

    List<EvidenceSubmission.EvidenceSubmissionRecord> findFinalizedByDealIdOrderByIdForUpdate(UUID dealId) {
        return jdbcTemplate.query("""
                SELECT * FROM fulfillment_evidence_submission
                WHERE deal_id = ? AND status IN ('SUBMITTED', 'ACCEPTED', 'REJECTED')
                ORDER BY id ASC
                FOR UPDATE
                """, this::mapSubmission, dealId);
    }

    boolean update(EvidenceSubmission.EvidenceSubmissionRecord submission, long previousVersion) {
        return jdbcTemplate.update("""
                UPDATE fulfillment_evidence_submission
                SET status = ?, object_version = ?, verified_size_bytes = ?,
                    verified_sha256 = ?, submitted_at = ?, accepted_at = ?,
                    rejected_at = ?, rejection_reason = ?, version = ?
                WHERE id = ? AND version = ?
                """, submission.status().name(), submission.objectVersion(),
                submission.verifiedSizeBytes(), submission.verifiedSha256(),
                timestamp(submission.submittedAt()), timestamp(submission.acceptedAt()),
                timestamp(submission.rejectedAt()), submission.rejectionReason(),
                submission.version(), submission.id(), previousVersion) == 1;
    }

    private EvidenceSubmission.EvidenceSubmissionRecord mapSubmission(ResultSet resultSet, int rowNumber)
            throws SQLException {
        return new EvidenceSubmission.EvidenceSubmissionRecord(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("deal_id", UUID.class),
                resultSet.getObject("milestone_id", UUID.class),
                resultSet.getObject("fulfillment_id", UUID.class),
                EvidenceType.valueOf(resultSet.getString("evidence_type")),
                EvidenceMediaType.fromValue(resultSet.getString("media_type")),
                resultSet.getString("file_name"),
                EvidenceSubmissionStatus.valueOf(resultSet.getString("status")),
                resultSet.getString("object_key"),
                resultSet.getString("object_version"),
                resultSet.getLong("client_size_bytes"),
                resultSet.getString("client_sha256"),
                resultSet.getObject("verified_size_bytes", Long.class),
                resultSet.getString("verified_sha256"),
                resultSet.getTimestamp("upload_expires_at").toInstant(),
                resultSet.getTimestamp("created_at").toInstant(),
                instant(resultSet, "submitted_at"),
                instant(resultSet, "accepted_at"),
                instant(resultSet, "rejected_at"),
                resultSet.getString("rejection_reason"),
                resultSet.getLong("version"));
    }

    private static Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private static Instant instant(ResultSet resultSet, String column)
            throws SQLException {
        Timestamp timestamp = resultSet.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }
}
