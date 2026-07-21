package com.m4trust.coreapi.casework;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class DisputeEvidenceSnapshotRepository {

    private final JdbcTemplate jdbcTemplate;

    DisputeEvidenceSnapshotRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    void insert(DisputeEvidenceSnapshotRecord snapshot) {
        insertAll(List.of(snapshot));
    }

    void insertAll(List<DisputeEvidenceSnapshotRecord> snapshots) {
        if (snapshots.isEmpty()) {
            return;
        }
        jdbcTemplate.batchUpdate("""
                INSERT INTO dispute_evidence_snapshot (
                    id, dispute_case_id, deal_id, evidence_submission_id, status_at_open, version_at_open,
                    evidence_type, media_type, file_name, object_version, verified_size_bytes, verified_sha256,
                    created_at, submitted_at, accepted_at, rejected_at, rejection_reason, video_job_id,
                    video_result_id
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                snapshots,
                snapshots.size(),
                (statement, snapshot) -> {
                    statement.setObject(1, snapshot.id());
                    statement.setObject(2, snapshot.disputeCaseId());
                    statement.setObject(3, snapshot.dealId());
                    statement.setObject(4, snapshot.evidenceSubmissionId());
                    statement.setString(5, snapshot.statusAtOpen());
                    statement.setLong(6, snapshot.versionAtOpen());
                    statement.setString(7, snapshot.evidenceType());
                    statement.setString(8, snapshot.mediaType());
                    statement.setString(9, snapshot.fileName());
                    statement.setString(10, snapshot.objectVersion());
                    statement.setLong(11, snapshot.verifiedSizeBytes());
                    statement.setString(12, snapshot.verifiedSha256());
                    statement.setTimestamp(13, Timestamp.from(snapshot.createdAt()));
                    statement.setTimestamp(14, timestamp(snapshot.submittedAt()));
                    statement.setTimestamp(15, timestamp(snapshot.acceptedAt()));
                    statement.setTimestamp(16, timestamp(snapshot.rejectedAt()));
                    statement.setString(17, snapshot.rejectionReason());
                    statement.setObject(18, snapshot.videoJobId());
                    statement.setObject(19, snapshot.videoResultId());
                });
    }

    List<DisputeEvidenceSnapshotRecord> findByDisputeCaseId(UUID disputeCaseId) {
        return jdbcTemplate.query("""
                SELECT * FROM dispute_evidence_snapshot
                WHERE dispute_case_id = ?
                ORDER BY evidence_submission_id ASC
                """, this::map, disputeCaseId);
    }

    private DisputeEvidenceSnapshotRecord map(ResultSet resultSet, int rowNum) throws SQLException {
        return new DisputeEvidenceSnapshotRecord(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("dispute_case_id", UUID.class),
                resultSet.getObject("deal_id", UUID.class),
                resultSet.getObject("evidence_submission_id", UUID.class),
                resultSet.getString("status_at_open"),
                resultSet.getLong("version_at_open"),
                resultSet.getString("evidence_type"),
                resultSet.getString("media_type"),
                resultSet.getString("file_name"),
                resultSet.getString("object_version"),
                resultSet.getLong("verified_size_bytes"),
                resultSet.getString("verified_sha256"),
                resultSet.getTimestamp("created_at").toInstant(),
                instant(resultSet.getTimestamp("submitted_at")),
                instant(resultSet.getTimestamp("accepted_at")),
                instant(resultSet.getTimestamp("rejected_at")),
                resultSet.getString("rejection_reason"),
                resultSet.getObject("video_job_id", UUID.class),
                resultSet.getObject("video_result_id", UUID.class));
    }

    record DisputeEvidenceSnapshotRecord(
            UUID id,
            UUID disputeCaseId,
            UUID dealId,
            UUID evidenceSubmissionId,
            String statusAtOpen,
            long versionAtOpen,
            String evidenceType,
            String mediaType,
            String fileName,
            String objectVersion,
            long verifiedSizeBytes,
            String verifiedSha256,
            Instant createdAt,
            Instant submittedAt,
            Instant acceptedAt,
            Instant rejectedAt,
            String rejectionReason,
            UUID videoJobId,
            UUID videoResultId) {
    }

    private static Timestamp timestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
