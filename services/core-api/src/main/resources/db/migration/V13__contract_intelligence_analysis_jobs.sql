CREATE TABLE contract_intelligence_analysis_job (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    deal_id UUID NOT NULL,
    document_id UUID NOT NULL,
    object_version TEXT NOT NULL,
    input_sha256 CHAR(64) NOT NULL,
    status TEXT NOT NULL,
    requested_at TIMESTAMPTZ NOT NULL,
    processing_started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    failed_at TIMESTAMPTZ,
    failure_code TEXT,
    retry_recommended BOOLEAN,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT contract_intelligence_analysis_job_status_ck CHECK (
        status IN ('QUEUED', 'PROCESSING', 'REVIEW_REQUIRED', 'FAILED', 'SUPERSEDED')
    ),
    CONSTRAINT contract_intelligence_analysis_job_sha_ck CHECK (
        input_sha256 ~ '^[0-9a-f]{64}$'
    ),
    CONSTRAINT contract_intelligence_analysis_job_version_ck CHECK (version >= 0),
    CONSTRAINT contract_intelligence_analysis_job_time_order_ck CHECK (
        (processing_started_at IS NULL OR processing_started_at >= requested_at)
        AND (completed_at IS NULL OR completed_at >= requested_at)
        AND (failed_at IS NULL OR failed_at >= requested_at)
    ),
    CONSTRAINT contract_intelligence_analysis_job_state_times_ck CHECK (
        (status = 'QUEUED'
            AND processing_started_at IS NULL
            AND completed_at IS NULL
            AND failed_at IS NULL
            AND failure_code IS NULL
            AND retry_recommended IS NULL)
        OR (status = 'PROCESSING'
            AND processing_started_at IS NOT NULL
            AND completed_at IS NULL
            AND failed_at IS NULL
            AND failure_code IS NULL
            AND retry_recommended IS NULL)
        OR (status = 'REVIEW_REQUIRED'
            AND processing_started_at IS NOT NULL
            AND completed_at IS NOT NULL
            AND failed_at IS NULL
            AND failure_code IS NULL
            AND retry_recommended IS NULL)
        OR (status = 'FAILED'
            AND completed_at IS NULL
            AND failed_at IS NOT NULL
            AND failure_code = btrim(failure_code)
            AND char_length(failure_code) BETWEEN 1 AND 100
            AND retry_recommended IS NOT NULL)
        OR (status = 'SUPERSEDED'
            AND completed_at IS NULL
            AND failed_at IS NULL
            AND failure_code IS NULL
            AND retry_recommended IS NULL)
    )
);
CREATE UNIQUE INDEX contract_intelligence_one_active_document_job_idx
 ON contract_intelligence_analysis_job(document_id) WHERE status IN ('QUEUED','PROCESSING');
CREATE INDEX contract_intelligence_analysis_job_document_idx
 ON contract_intelligence_analysis_job(document_id, requested_at DESC);
