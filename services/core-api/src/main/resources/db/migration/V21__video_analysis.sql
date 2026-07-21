-- Slice 13: Video Analysis V1 persistence
-- Forward-only migration; V15-V20 remain frozen.

ALTER TABLE fulfillment_evidence_submission
    ADD CONSTRAINT fulfillment_evidence_submission_id_deal_id_unique UNIQUE (id, deal_id);

CREATE TABLE fulfillment_video_analysis_job (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    deal_id UUID NOT NULL REFERENCES deal(id),
    fulfillment_id UUID NOT NULL,
    milestone_id UUID NOT NULL,
    evidence_submission_id UUID NOT NULL,
    object_version TEXT NOT NULL,
    input_sha256 CHAR(64) NOT NULL,
    input_size_bytes BIGINT NOT NULL,
    input_media_type TEXT NOT NULL,
    input_file_name TEXT NOT NULL,
    status TEXT NOT NULL,
    predecessor_job_id UUID REFERENCES fulfillment_video_analysis_job(id),
    requested_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    failed_at TIMESTAMPTZ,
    failure_code TEXT,
    retry_recommended BOOLEAN,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fulfillment_video_analysis_job_fulfillment_fk
        FOREIGN KEY (fulfillment_id, deal_id) REFERENCES fulfillment(id, deal_id),
    CONSTRAINT fulfillment_video_analysis_job_milestone_fk
        FOREIGN KEY (milestone_id, fulfillment_id, deal_id)
        REFERENCES fulfillment_milestone(id, fulfillment_id, deal_id),
    CONSTRAINT fulfillment_video_analysis_job_evidence_fk
        FOREIGN KEY (evidence_submission_id, deal_id)
        REFERENCES fulfillment_evidence_submission(id, deal_id),
    CONSTRAINT fulfillment_video_analysis_job_deal_hosting_tenant_fk
        FOREIGN KEY (deal_id, tenant_id) REFERENCES deal(id, tenant_id),
    CONSTRAINT fulfillment_video_analysis_job_status_ck CHECK (
        status IN ('QUEUED', 'RESULT_AVAILABLE', 'FAILED')
    ),
    CONSTRAINT fulfillment_video_analysis_job_sha_ck CHECK (
        input_sha256 ~ '^[0-9a-f]{64}$'
    ),
    CONSTRAINT fulfillment_video_analysis_job_size_ck CHECK (input_size_bytes > 0),
    CONSTRAINT fulfillment_video_analysis_job_media_ck CHECK (input_media_type = 'video/mp4'),
    CONSTRAINT fulfillment_video_analysis_job_version_ck CHECK (version >= 0),
    CONSTRAINT fulfillment_video_analysis_job_predecessor_not_self_ck CHECK (
        predecessor_job_id IS NULL OR predecessor_job_id <> id
    ),
    CONSTRAINT fulfillment_video_analysis_job_time_order_ck CHECK (
        (completed_at IS NULL OR completed_at >= requested_at)
        AND (failed_at IS NULL OR failed_at >= requested_at)
    ),
    CONSTRAINT fulfillment_video_analysis_job_failure_pair_ck CHECK (
        (failure_code IS NULL AND retry_recommended IS NULL)
        OR (failure_code = btrim(failure_code)
            AND char_length(failure_code) BETWEEN 1 AND 100
            AND retry_recommended IS NOT NULL)
    ),
    CONSTRAINT fulfillment_video_analysis_job_state_times_ck CHECK (
        (status = 'QUEUED'
            AND completed_at IS NULL
            AND failed_at IS NULL
            AND failure_code IS NULL)
        OR (status = 'RESULT_AVAILABLE'
            AND completed_at IS NOT NULL
            AND failed_at IS NULL
            AND failure_code IS NULL)
        OR (status = 'FAILED'
            AND completed_at IS NULL
            AND failed_at IS NOT NULL
            AND failure_code IS NOT NULL)
    )
);

CREATE UNIQUE INDEX fulfillment_video_analysis_one_queued_job_idx
    ON fulfillment_video_analysis_job(evidence_submission_id)
    WHERE status = 'QUEUED';

CREATE UNIQUE INDEX fulfillment_video_analysis_one_successful_job_idx
    ON fulfillment_video_analysis_job(evidence_submission_id)
    WHERE status = 'RESULT_AVAILABLE';

CREATE INDEX fulfillment_video_analysis_job_evidence_idx
    ON fulfillment_video_analysis_job(evidence_submission_id, requested_at DESC);

CREATE TABLE fulfillment_video_analysis_result (
    id UUID PRIMARY KEY,
    job_id UUID NOT NULL UNIQUE REFERENCES fulfillment_video_analysis_job(id),
    schema_version TEXT NOT NULL,
    canonical_result JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fulfillment_video_analysis_result_schema_version_ck CHECK (
        schema_version = '1.0.0'
    ),
    CONSTRAINT fulfillment_video_analysis_result_object_ck CHECK (
        jsonb_typeof(canonical_result) = 'object'
    )
);

CREATE FUNCTION enforce_fulfillment_video_analysis_predecessor_evidence()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    predecessor_evidence UUID;
BEGIN
    IF NEW.predecessor_job_id IS NOT NULL THEN
        SELECT evidence_submission_id
        INTO predecessor_evidence
        FROM fulfillment_video_analysis_job
        WHERE id = NEW.predecessor_job_id;

        IF predecessor_evidence IS NULL
            OR predecessor_evidence IS DISTINCT FROM NEW.evidence_submission_id THEN
            RAISE EXCEPTION 'video analysis predecessor must belong to the same evidence submission';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER fulfillment_video_analysis_predecessor_evidence
BEFORE INSERT OR UPDATE ON fulfillment_video_analysis_job
FOR EACH ROW
EXECUTE FUNCTION enforce_fulfillment_video_analysis_predecessor_evidence();

CREATE FUNCTION prevent_fulfillment_video_analysis_result_mutation()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'video analysis result is immutable';
END;
$$;

CREATE TRIGGER fulfillment_video_analysis_result_immutable
BEFORE UPDATE OR DELETE ON fulfillment_video_analysis_result
FOR EACH ROW
EXECUTE FUNCTION prevent_fulfillment_video_analysis_result_mutation();
