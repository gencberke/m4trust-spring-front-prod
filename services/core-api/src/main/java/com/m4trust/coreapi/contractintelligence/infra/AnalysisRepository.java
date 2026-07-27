package com.m4trust.coreapi.contractintelligence.infra;

import com.m4trust.coreapi.api.api.ApiErrorCode;
import com.m4trust.coreapi.contractintelligence.api.AnalysisExceptions;
import com.m4trust.coreapi.contractintelligence.api.dto.*;
import com.m4trust.coreapi.contractintelligence.domain.*;
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
public class AnalysisRepository {

  private final JdbcTemplate jdbcTemplate;

  public AnalysisRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public Optional<AnalysisJob> findLatestForDocument(UUID documentId) {
    return jdbcTemplate
        .query(
            """
                SELECT id, tenant_id, deal_id, document_id, object_version, input_sha256,
                       status, requested_at, processing_started_at, completed_at, failed_at,
                       failure_code, retry_recommended, version
                FROM contract_intelligence_analysis_job
                WHERE document_id = ?
                ORDER BY requested_at DESC, id DESC
                LIMIT 1
                """,
            this::map,
            documentId)
        .stream()
        .findFirst();
  }

  public Optional<AnalysisJob> findById(UUID jobId) {
    return jdbcTemplate
        .query(
            """
                SELECT id, tenant_id, deal_id, document_id, object_version, input_sha256,
                       status, requested_at, processing_started_at, completed_at, failed_at,
                       failure_code, retry_recommended, version
                FROM contract_intelligence_analysis_job
                WHERE id = ?
                """,
            this::map,
            jobId)
        .stream()
        .findFirst();
  }

  public Optional<AnalysisJob> findByIdForUpdate(UUID jobId) {
    return jdbcTemplate
        .query(
            """
                SELECT id, tenant_id, deal_id, document_id, object_version, input_sha256,
                       status, requested_at, processing_started_at, completed_at, failed_at,
                       failure_code, retry_recommended, version
                FROM contract_intelligence_analysis_job WHERE id = ? FOR UPDATE
                """,
            this::map,
            jobId)
        .stream()
        .findFirst();
  }

  public boolean hasActiveJob(UUID documentId) {
    return Boolean.TRUE.equals(
        jdbcTemplate.queryForObject(
            """
                SELECT EXISTS (
                    SELECT 1
                    FROM contract_intelligence_analysis_job
                    WHERE document_id = ? AND status IN ('QUEUED', 'PROCESSING')
                )
                """,
            Boolean.class,
            documentId));
  }

  public void insertQueued(AnalysisJob job) {
    jdbcTemplate.update(
        """
                INSERT INTO contract_intelligence_analysis_job (
                    id, tenant_id, deal_id, document_id, object_version, input_sha256,
                    status, requested_at, version
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
        job.id(),
        job.tenantId(),
        job.dealId(),
        job.documentId(),
        job.objectVersion(),
        job.inputSha256(),
        job.status().name(),
        Timestamp.from(job.requestedAt()),
        job.version());
  }

  public void complete(AnalysisJob job, Instant processingStartedAt, Instant completedAt) {
    int changed =
        jdbcTemplate.update(
            """
                UPDATE contract_intelligence_analysis_job
                SET status = 'REVIEW_REQUIRED', processing_started_at = ?, completed_at = ?, version = version + 1
                WHERE id = ? AND version = ? AND status IN ('QUEUED', 'PROCESSING')
                """,
            Timestamp.from(processingStartedAt),
            Timestamp.from(completedAt),
            job.id(),
            job.version());
    if (changed != 1) throw new IllegalStateException("Analysis job changed while completing");
  }

  public void fail(AnalysisJob job, Instant failedAt, String code, boolean retryRecommended) {
    int changed =
        jdbcTemplate.update(
            """
                UPDATE contract_intelligence_analysis_job
                SET status = 'FAILED', failed_at = ?, failure_code = ?, retry_recommended = ?, version = version + 1
                WHERE id = ? AND version = ? AND status IN ('QUEUED', 'PROCESSING')
                """,
            Timestamp.from(failedAt),
            code,
            retryRecommended,
            job.id(),
            job.version());
    if (changed != 1) throw new IllegalStateException("Analysis job changed while failing");
  }

  public void accept(AnalysisJob job) {
    if (jdbcTemplate.update(
            """
                UPDATE contract_intelligence_analysis_job SET status = 'ACCEPTED', version = version + 1
                WHERE id = ? AND version = ? AND status = 'REVIEW_REQUIRED'
                """,
            job.id(),
            job.version())
        != 1) {
      throw new AnalysisExceptions.Conflict(ApiErrorCode.DEAL_STATE_CONFLICT);
    }
  }

  public Optional<UUID> findResultId(UUID jobId) {
    return jdbcTemplate
        .query(
            "SELECT id FROM contract_intelligence_extraction_result_version WHERE analysis_job_id = ?",
            (rs, row) -> rs.getObject(1, UUID.class),
            jobId)
        .stream()
        .findFirst();
  }

  public void insertResult(
      UUID resultId, UUID jobId, String schemaVersion, String canonicalResult, Instant createdAt) {
    jdbcTemplate.update(
        """
                INSERT INTO contract_intelligence_extraction_result_version
                    (id, analysis_job_id, schema_version, canonical_result, created_at)
                VALUES (?, ?, ?, CAST(? AS jsonb), ?)
                """,
        resultId,
        jobId,
        schemaVersion,
        canonicalResult,
        Timestamp.from(createdAt));
  }

  public Optional<String> findResult(UUID jobId) {
    return jdbcTemplate
        .query(
            """
                SELECT canonical_result::text FROM contract_intelligence_extraction_result_version
                WHERE analysis_job_id = ?
                """,
            (rs, row) -> rs.getString(1),
            jobId)
        .stream()
        .findFirst();
  }

  public List<AnalysisJob> lockAndSupersedeDocument(UUID documentId) {
    List<AnalysisJob> jobs =
        jdbcTemplate.query(
            """
                SELECT id, tenant_id, deal_id, document_id, object_version, input_sha256,
                       status, requested_at, processing_started_at, completed_at, failed_at,
                       failure_code, retry_recommended, version
                FROM contract_intelligence_analysis_job
                WHERE document_id = ? AND status <> 'SUPERSEDED' FOR UPDATE
                """,
            this::map,
            documentId);
    for (AnalysisJob job : jobs) {
      if (jdbcTemplate.update(
              """
                    UPDATE contract_intelligence_analysis_job
                    SET status = 'SUPERSEDED', version = version + 1
                    WHERE id = ? AND version = ?
                    """,
              job.id(),
              job.version())
          != 1) {
        throw new IllegalStateException("Analysis job changed while superseding");
      }
    }
    return jobs;
  }

  private AnalysisJob map(ResultSet resultSet, int rowNumber) throws SQLException {
    return new AnalysisJob(
        resultSet.getObject("id", UUID.class),
        resultSet.getObject("tenant_id", UUID.class),
        resultSet.getObject("deal_id", UUID.class),
        resultSet.getObject("document_id", UUID.class),
        resultSet.getString("object_version"),
        resultSet.getString("input_sha256"),
        AnalysisJobStatus.valueOf(resultSet.getString("status")),
        resultSet.getTimestamp("requested_at").toInstant(),
        instant(resultSet, "processing_started_at"),
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

  public record AnalysisJob(
      UUID id,
      UUID tenantId,
      UUID dealId,
      UUID documentId,
      String objectVersion,
      String inputSha256,
      AnalysisJobStatus status,
      Instant requestedAt,
      Instant processingStartedAt,
      Instant completedAt,
      Instant failedAt,
      String failureCode,
      Boolean retryRecommended,
      long version) {

    public static AnalysisJob queued(
        UUID id,
        UUID tenantId,
        UUID dealId,
        UUID documentId,
        String objectVersion,
        String inputSha256,
        Instant requestedAt) {
      return new AnalysisJob(
          id,
          tenantId,
          dealId,
          documentId,
          objectVersion,
          inputSha256,
          AnalysisJobStatus.QUEUED,
          requestedAt,
          null,
          null,
          null,
          null,
          null,
          0);
    }
  }
}
