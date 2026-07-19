-- Manual review accepts the technical extraction into an immutable rule-set chain.
-- It is not commercial acceptance and it does not ratify the Deal.
ALTER TABLE contract_intelligence_analysis_job
    DROP CONSTRAINT contract_intelligence_analysis_job_status_ck,
    DROP CONSTRAINT contract_intelligence_analysis_job_state_times_ck,
    ADD CONSTRAINT contract_intelligence_analysis_job_status_ck CHECK (
        status IN ('QUEUED', 'PROCESSING', 'REVIEW_REQUIRED', 'ACCEPTED', 'FAILED', 'SUPERSEDED')
    ),
    ADD CONSTRAINT contract_intelligence_analysis_job_state_times_ck CHECK (
        (status = 'QUEUED'
            AND processing_started_at IS NULL AND completed_at IS NULL AND failed_at IS NULL AND failure_code IS NULL)
        OR (status = 'PROCESSING'
            AND processing_started_at IS NOT NULL AND completed_at IS NULL AND failed_at IS NULL AND failure_code IS NULL)
        OR (status IN ('REVIEW_REQUIRED', 'ACCEPTED')
            AND processing_started_at IS NOT NULL AND completed_at IS NOT NULL AND failed_at IS NULL AND failure_code IS NULL)
        OR (status = 'FAILED'
            AND completed_at IS NULL AND failed_at IS NOT NULL AND failure_code IS NOT NULL)
        OR (status = 'SUPERSEDED'
            AND NOT (completed_at IS NOT NULL AND failed_at IS NOT NULL)
            AND (completed_at IS NULL OR processing_started_at IS NOT NULL))
    );
