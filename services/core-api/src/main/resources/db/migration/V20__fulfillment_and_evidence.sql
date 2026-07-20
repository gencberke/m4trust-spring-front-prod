-- Slice 12: Fulfillment and Evidence V1
-- Forward-only migration; V15-V19 remain frozen.

CREATE TABLE fulfillment (
    id UUID PRIMARY KEY,
    deal_id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    source_package_id UUID NOT NULL,
    status VARCHAR(32) NOT NULL,
    version BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fulfillment_deal_id_unique UNIQUE (deal_id),
    CONSTRAINT fulfillment_status_check CHECK (status IN ('NOT_STARTED', 'IN_PROGRESS', 'EVIDENCE_REQUIRED', 'REVIEW_REQUIRED', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT fulfillment_version_non_negative CHECK (version >= 0)
);

CREATE INDEX idx_fulfillment_deal_id ON fulfillment(deal_id);

CREATE TABLE fulfillment_milestone (
    id UUID PRIMARY KEY,
    fulfillment_id UUID NOT NULL,
    deal_id UUID NOT NULL,
    title VARCHAR(200) NOT NULL,
    description TEXT,
    status VARCHAR(32) NOT NULL,
    version BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fulfillment_milestone_fulfillment_id_unique UNIQUE (fulfillment_id),
    CONSTRAINT fulfillment_milestone_status_check CHECK (status IN ('NOT_STARTED', 'IN_PROGRESS', 'EVIDENCE_REQUIRED', 'REVIEW_REQUIRED', 'COMPLETED')),
    CONSTRAINT fulfillment_milestone_version_non_negative CHECK (version >= 0),
    CONSTRAINT fulfillment_milestone_fulfillment_fk FOREIGN KEY (fulfillment_id) REFERENCES fulfillment(id)
);

CREATE INDEX idx_fulfillment_milestone_fulfillment_id ON fulfillment_milestone(fulfillment_id);
CREATE INDEX idx_fulfillment_milestone_deal_id ON fulfillment_milestone(deal_id);

CREATE TABLE fulfillment_milestone_rule_reference (
    milestone_id UUID NOT NULL,
    rule_reference VARCHAR(255) NOT NULL,
    category VARCHAR(32) NOT NULL,
    PRIMARY KEY (milestone_id, rule_reference),
    CONSTRAINT fulfillment_milestone_rule_reference_category_check CHECK (category IN ('PAYMENT', 'DELIVERY', 'QUALITY', 'PENALTY', 'TERMINATION', 'DISPUTE', 'OTHER', 'UNKNOWN')),
    CONSTRAINT fulfillment_milestone_rule_reference_milestone_fk FOREIGN KEY (milestone_id) REFERENCES fulfillment_milestone(id)
);

CREATE TABLE fulfillment_evidence_submission (
    id UUID PRIMARY KEY,
    deal_id UUID NOT NULL,
    milestone_id UUID NOT NULL,
    fulfillment_id UUID NOT NULL,
    evidence_type VARCHAR(32) NOT NULL,
    media_type VARCHAR(128) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL,
    object_key VARCHAR(512) NOT NULL,
    object_version VARCHAR(512),
    client_size_bytes BIGINT NOT NULL,
    client_sha256 VARCHAR(64) NOT NULL,
    verified_size_bytes BIGINT,
    verified_sha256 VARCHAR(64),
    upload_expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    submitted_at TIMESTAMPTZ,
    accepted_at TIMESTAMPTZ,
    rejected_at TIMESTAMPTZ,
    rejection_reason VARCHAR(1000),
    version BIGINT NOT NULL,
    CONSTRAINT fulfillment_evidence_submission_status_check CHECK (status IN ('PENDING_UPLOAD', 'SUBMITTED', 'ACCEPTED', 'REJECTED')),
    CONSTRAINT fulfillment_evidence_submission_version_non_negative CHECK (version >= 0),
    CONSTRAINT fulfillment_evidence_submission_client_sha256_check CHECK (client_sha256 ~ '^[a-f0-9]{64}$'),
    CONSTRAINT fulfillment_evidence_submission_verified_sha256_check CHECK (verified_sha256 IS NULL OR verified_sha256 ~ '^[a-f0-9]{64}$'),
    CONSTRAINT fulfillment_evidence_submission_milestone_fk FOREIGN KEY (milestone_id) REFERENCES fulfillment_milestone(id),
    CONSTRAINT fulfillment_evidence_submission_fulfillment_fk FOREIGN KEY (fulfillment_id) REFERENCES fulfillment(id)
);

CREATE INDEX idx_fulfillment_evidence_submission_milestone_id ON fulfillment_evidence_submission(milestone_id);
CREATE INDEX idx_fulfillment_evidence_submission_deal_id ON fulfillment_evidence_submission(deal_id);

-- At most one current SUBMITTED evidence per milestone.
CREATE UNIQUE INDEX idx_fulfillment_evidence_submission_current_submitted
    ON fulfillment_evidence_submission(milestone_id)
    WHERE status = 'SUBMITTED';

-- Same-deal ownership between submission, milestone, and fulfillment is enforced
-- by explicit application-layer checks on submission.deal_id and milestone.deal_id.
