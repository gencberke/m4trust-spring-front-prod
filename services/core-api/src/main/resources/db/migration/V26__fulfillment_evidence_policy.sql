-- Plan 18B-P3: persist effective evidence policy on fulfillment.
-- Forward-only; historical rows backfill to REQUIRED (v1/v2 effective behavior).

ALTER TABLE fulfillment
    ADD COLUMN evidence_policy VARCHAR(32);

UPDATE fulfillment
SET evidence_policy = 'REQUIRED'
WHERE evidence_policy IS NULL;

ALTER TABLE fulfillment
    ALTER COLUMN evidence_policy SET NOT NULL;

ALTER TABLE fulfillment
    ADD CONSTRAINT fulfillment_evidence_policy_ck
    CHECK (evidence_policy IN ('REQUIRED', 'NOT_REQUIRED'));
