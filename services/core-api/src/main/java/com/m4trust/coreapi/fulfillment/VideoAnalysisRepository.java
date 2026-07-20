package com.m4trust.coreapi.fulfillment;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class VideoAnalysisRepository {

    private final JdbcTemplate jdbcTemplate;

    VideoAnalysisRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    Optional<VideoAnalysisJobRecord> findLatestByEvidenceId(UUID evidenceSubmissionId) {
        return jdbcTemplate.query("""
                SELECT id, tenant_id, deal_id, fulfillment_id, milestone_id, evidence_submission_id,
                       object_version, input_sha256, input_size_bytes, input_media_type, input_file_name,
                       status, predecessor_job_id, requested_at, completed_at, failed_at,
                       failure_code, retry_recommended, version
                FROM fulfillment_video_analysis_job
                WHERE evidence_submission_id = ?
                ORDER BY requested_at DESC, id DESC
                LIMIT 1
                """, this::mapJob, evidenceSubmissionId).stream().findFirst();
    }

    Optional<VideoAnalysisJobRecord> findByIdForUpdate(UUID jobId) {
        return jdbcTemplate.query("""
                SELECT id, tenant_id, deal_id, fulfillment_id, milestone_id, evidence_submission_id,
                       object_version, input_sha256, input_size_bytes, input_media_type, input_file_name,
                       status, predecessor_job_id, requested_at, completed_at, failed_at,
                       failure_code, retry_recommended, version
                FROM fulfillment_video_analysis_job
                WHERE id = ?
                FOR UPDATE
                """, this::mapJob, jobId).stream().findFirst();
    }

    boolean hasQueuedJob(UUID evidenceSubmissionId) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject("""
                SELECT EXISTS (
                    SELECT 1
                    FROM fulfillment_video_analysis_job
                    WHERE evidence_submission_id = ? AND status = 'QUEUED'
                )
                """, Boolean.class, evidenceSubmissionId));
    }

    boolean hasSuccessfulJob(UUID evidenceSubmissionId) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject("""
                SELECT EXISTS (
                    SELECT 1
                    FROM fulfillment_video_analysis_job
                    WHERE evidence_submission_id = ? AND status = 'RESULT_AVAILABLE'
                )
                """, Boolean.class, evidenceSubmissionId));
    }

    void insertQueued(VideoAnalysisJobRecord job) {
        jdbcTemplate.update("""
                INSERT INTO fulfillment_video_analysis_job (
                    id, tenant_id, deal_id, fulfillment_id, milestone_id, evidence_submission_id,
                    object_version, input_sha256, input_size_bytes, input_media_type, input_file_name,
                    status, predecessor_job_id, requested_at, version
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, job.id(), job.tenantId(), job.dealId(), job.fulfillmentId(), job.milestoneId(),
                job.evidenceSubmissionId(), job.objectVersion(), job.inputSha256(), job.inputSizeBytes(),
                job.inputMediaType(), job.inputFileName(), job.status().name(), job.predecessorJobId(),
                Timestamp.from(job.requestedAt()), job.version());
    }

    void insertResult(UUID resultId, UUID jobId, String schemaVersion, String canonicalResult,
            Instant createdAt) {
        jdbcTemplate.update("""
                INSERT INTO fulfillment_video_analysis_result
                    (id, job_id, schema_version, canonical_result, created_at)
                VALUES (?, ?, ?, CAST(? AS jsonb), ?)
                """, resultId, jobId, schemaVersion, canonicalResult, Timestamp.from(createdAt));
    }

    Optional<String> findResultByJobId(UUID jobId) {
        return jdbcTemplate.query("""
                SELECT canonical_result::text
                FROM fulfillment_video_analysis_result
                WHERE job_id = ?
                """, (resultSet, rowNumber) -> resultSet.getString(1), jobId).stream().findFirst();
    }

    private VideoAnalysisJobRecord mapJob(ResultSet resultSet, int rowNumber) throws SQLException {
        return new VideoAnalysisJobRecord(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("tenant_id", UUID.class),
                resultSet.getObject("deal_id", UUID.class),
                resultSet.getObject("fulfillment_id", UUID.class),
                resultSet.getObject("milestone_id", UUID.class),
                resultSet.getObject("evidence_submission_id", UUID.class),
                resultSet.getString("object_version"),
                resultSet.getString("input_sha256"),
                resultSet.getLong("input_size_bytes"),
                resultSet.getString("input_media_type"),
                resultSet.getString("input_file_name"),
                VideoAnalysisJobStatus.valueOf(resultSet.getString("status")),
                resultSet.getObject("predecessor_job_id", UUID.class),
                resultSet.getTimestamp("requested_at").toInstant(),
                instant(resultSet, "completed_at"),
                instant(resultSet, "failed_at"),
                resultSet.getString("failure_code"),
                resultSet.getObject("retry_recommended", Boolean.class),
                resultSet.getLong("version"));
    }

    private static Instant instant(ResultSet resultSet, String column) throws SQLException {
        Timestamp value = resultSet.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    record VideoAnalysisJobRecord(UUID id, UUID tenantId, UUID dealId, UUID fulfillmentId,
            UUID milestoneId, UUID evidenceSubmissionId, String objectVersion, String inputSha256,
            long inputSizeBytes, String inputMediaType, String inputFileName,
            VideoAnalysisJobStatus status, UUID predecessorJobId, Instant requestedAt,
            Instant completedAt, Instant failedAt, String failureCode, Boolean retryRecommended,
            long version) {

        static VideoAnalysisJobRecord queued(UUID id, UUID tenantId, UUID dealId, UUID fulfillmentId,
                UUID milestoneId, UUID evidenceSubmissionId, String objectVersion, String inputSha256,
                long inputSizeBytes, String inputMediaType, String inputFileName, UUID predecessorJobId,
                Instant requestedAt) {
            return new VideoAnalysisJobRecord(id, tenantId, dealId, fulfillmentId, milestoneId,
                    evidenceSubmissionId, objectVersion, inputSha256, inputSizeBytes, inputMediaType,
                    inputFileName, VideoAnalysisJobStatus.QUEUED, predecessorJobId, requestedAt,
                    null, null, null, null, 0);
        }
    }
}
