CREATE TABLE contract_intelligence_analysis_job (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    deal_id UUID NOT NULL REFERENCES deal(id),
    document_id UUID NOT NULL REFERENCES document(id),
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
    CONSTRAINT contract_intelligence_analysis_job_status_ck CHECK (status IN ('QUEUED','PROCESSING','REVIEW_REQUIRED','FAILED','SUPERSEDED')),
    CONSTRAINT contract_intelligence_analysis_job_sha_ck CHECK (input_sha256 ~ '^[0-9a-f]{64}$')
);
CREATE UNIQUE INDEX contract_intelligence_one_active_document_job_idx
 ON contract_intelligence_analysis_job(document_id) WHERE status IN ('QUEUED','PROCESSING');
CREATE INDEX contract_intelligence_analysis_job_document_idx
 ON contract_intelligence_analysis_job(document_id, requested_at DESC);
