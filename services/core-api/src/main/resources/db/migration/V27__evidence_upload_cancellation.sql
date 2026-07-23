-- Plan 18C-P2: pending evidence upload cancellation metadata.
-- Forward-only; V20-V26 remain frozen.

ALTER TABLE fulfillment_evidence_submission
    ADD COLUMN cancelled_at TIMESTAMPTZ;

ALTER TABLE fulfillment_evidence_submission
    DROP CONSTRAINT fulfillment_evidence_submission_lifecycle_check;

ALTER TABLE fulfillment_evidence_submission
    ADD CONSTRAINT fulfillment_evidence_submission_lifecycle_check CHECK (
        (cancelled_at IS NULL OR status = 'PENDING_UPLOAD')
        AND
        (status = 'PENDING_UPLOAD'
            AND object_version IS NULL
            AND verified_size_bytes IS NULL
            AND verified_sha256 IS NULL
            AND submitted_at IS NULL
            AND accepted_at IS NULL
            AND rejected_at IS NULL
            AND rejection_reason IS NULL
            AND (cancelled_at IS NULL OR cancelled_at >= created_at))
        OR
        (status = 'SUBMITTED'
            AND cancelled_at IS NULL
            AND object_version IS NOT NULL
            AND verified_size_bytes IS NOT NULL
            AND verified_sha256 IS NOT NULL
            AND submitted_at IS NOT NULL
            AND accepted_at IS NULL
            AND rejected_at IS NULL
            AND rejection_reason IS NULL)
        OR
        (status = 'ACCEPTED'
            AND cancelled_at IS NULL
            AND object_version IS NOT NULL
            AND verified_size_bytes IS NOT NULL
            AND verified_sha256 IS NOT NULL
            AND submitted_at IS NOT NULL
            AND accepted_at IS NOT NULL
            AND rejected_at IS NULL
            AND rejection_reason IS NULL)
        OR
        (status = 'REJECTED'
            AND cancelled_at IS NULL
            AND object_version IS NOT NULL
            AND verified_size_bytes IS NOT NULL
            AND verified_sha256 IS NOT NULL
            AND submitted_at IS NOT NULL
            AND accepted_at IS NULL
            AND rejected_at IS NOT NULL
            AND rejection_reason IS NOT NULL)
    );
