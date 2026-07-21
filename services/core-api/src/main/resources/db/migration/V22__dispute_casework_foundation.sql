-- Slice 14A: Dispute and casework foundation persistence
-- Forward-only migration; V15-V21 remain frozen.

CREATE TABLE dispute_case (
    id UUID PRIMARY KEY,
    deal_id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    fulfillment_id UUID NOT NULL,
    milestone_id UUID NOT NULL,
    ratification_package_id UUID NOT NULL,
    fulfillment_status_at_open VARCHAR(32) NOT NULL,
    fulfillment_version_at_open BIGINT NOT NULL,
    milestone_version_at_open BIGINT NOT NULL,
    reason_code VARCHAR(64) NOT NULL,
    subject VARCHAR(200) NOT NULL,
    statement TEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    opening_tenant_id UUID NOT NULL,
    opening_legal_entity_id UUID NOT NULL,
    opening_user_id UUID NOT NULL,
    opening_legal_name VARCHAR(200) NOT NULL,
    opened_at TIMESTAMPTZ NOT NULL,
    acknowledged_at TIMESTAMPTZ,
    withdrawn_at TIMESTAMPTZ,
    version BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT dispute_case_id_deal_id_unique UNIQUE (id, deal_id),
    CONSTRAINT dispute_case_deal_hosting_tenant_fk
        FOREIGN KEY (deal_id, tenant_id) REFERENCES deal(id, tenant_id),
    CONSTRAINT dispute_case_fulfillment_fk
        FOREIGN KEY (fulfillment_id, deal_id) REFERENCES fulfillment(id, deal_id),
    CONSTRAINT dispute_case_milestone_fk
        FOREIGN KEY (milestone_id, fulfillment_id, deal_id)
        REFERENCES fulfillment_milestone(id, fulfillment_id, deal_id),
    CONSTRAINT dispute_case_ratification_package_fk
        FOREIGN KEY (deal_id, ratification_package_id)
        REFERENCES ratification_package(deal_id, id),
    CONSTRAINT dispute_case_opening_party_fk
        FOREIGN KEY (deal_id, opening_legal_entity_id)
        REFERENCES deal_participant(deal_id, legal_entity_id),
    CONSTRAINT dispute_case_opening_user_fk
        FOREIGN KEY (opening_user_id) REFERENCES identity_user(id),
    CONSTRAINT dispute_case_opening_actor_user_tenant_fk
        FOREIGN KEY (opening_user_id, opening_tenant_id)
        REFERENCES tenant_user(user_id, tenant_id),
    CONSTRAINT dispute_case_opening_actor_entity_tenant_fk
        FOREIGN KEY (opening_legal_entity_id, opening_tenant_id)
        REFERENCES legal_entity(id, tenant_id),
    CONSTRAINT dispute_case_reason_ck CHECK (
        reason_code IN (
            'NON_DELIVERY',
            'EVIDENCE_QUALITY',
            'EVIDENCE_REJECTION',
            'CONTRACT_NON_CONFORMANCE',
            'OTHER'
        )
    ),
    CONSTRAINT dispute_case_status_ck CHECK (
        status IN ('OPEN', 'UNDER_REVIEW', 'RESOLVED', 'WITHDRAWN')
    ),
    CONSTRAINT dispute_case_fulfillment_status_at_open_ck CHECK (
        fulfillment_status_at_open IN (
            'IN_PROGRESS',
            'EVIDENCE_REQUIRED',
            'REVIEW_REQUIRED',
            'COMPLETED'
        )
    ),
    CONSTRAINT dispute_case_subject_ck CHECK (
        subject = btrim(subject)
        AND char_length(subject) BETWEEN 1 AND 200
    ),
    CONSTRAINT dispute_case_statement_ck CHECK (
        statement = btrim(statement)
        AND char_length(statement) BETWEEN 1 AND 4000
    ),
    CONSTRAINT dispute_case_version_ck CHECK (version >= 0),
    CONSTRAINT dispute_case_fulfillment_version_ck CHECK (fulfillment_version_at_open >= 0),
    CONSTRAINT dispute_case_milestone_version_ck CHECK (milestone_version_at_open >= 0),
    CONSTRAINT dispute_case_opening_legal_name_ck CHECK (
        opening_legal_name = btrim(opening_legal_name)
        AND char_length(opening_legal_name) BETWEEN 1 AND 200
    ),
    CONSTRAINT dispute_case_timestamps_ck CHECK (
        updated_at >= created_at
        AND opened_at >= created_at
        AND (acknowledged_at IS NULL OR acknowledged_at >= opened_at)
        AND (withdrawn_at IS NULL OR withdrawn_at >= opened_at)
    ),
    CONSTRAINT dispute_case_status_times_ck CHECK (
        (status = 'OPEN' AND acknowledged_at IS NULL AND withdrawn_at IS NULL)
        OR (status = 'UNDER_REVIEW' AND acknowledged_at IS NOT NULL AND withdrawn_at IS NULL)
        OR (status = 'WITHDRAWN' AND withdrawn_at IS NOT NULL)
        OR status = 'RESOLVED'
    )
);

CREATE UNIQUE INDEX dispute_case_one_active_per_deal_idx
    ON dispute_case(deal_id)
    WHERE status IN ('OPEN', 'UNDER_REVIEW');

CREATE INDEX dispute_case_deal_opened_at_id_idx
    ON dispute_case(deal_id, opened_at DESC, id DESC);

CREATE TABLE dispute_evidence_snapshot (
    id UUID PRIMARY KEY,
    dispute_case_id UUID NOT NULL,
    deal_id UUID NOT NULL,
    evidence_submission_id UUID NOT NULL,
    status_at_open VARCHAR(32) NOT NULL,
    version_at_open BIGINT NOT NULL,
    evidence_type VARCHAR(32) NOT NULL,
    media_type VARCHAR(128) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    object_version VARCHAR(512) NOT NULL,
    verified_size_bytes BIGINT NOT NULL,
    verified_sha256 CHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    submitted_at TIMESTAMPTZ,
    accepted_at TIMESTAMPTZ,
    rejected_at TIMESTAMPTZ,
    rejection_reason VARCHAR(1000),
    video_job_id UUID,
    video_result_id UUID,
    CONSTRAINT dispute_evidence_snapshot_case_deal_fk
        FOREIGN KEY (dispute_case_id, deal_id) REFERENCES dispute_case(id, deal_id),
    CONSTRAINT dispute_evidence_snapshot_evidence_fk
        FOREIGN KEY (evidence_submission_id, deal_id)
        REFERENCES fulfillment_evidence_submission(id, deal_id),
    CONSTRAINT dispute_evidence_snapshot_video_job_fk
        FOREIGN KEY (video_job_id) REFERENCES fulfillment_video_analysis_job(id),
    CONSTRAINT dispute_evidence_snapshot_video_result_fk
        FOREIGN KEY (video_result_id) REFERENCES fulfillment_video_analysis_result(id),
    CONSTRAINT dispute_evidence_snapshot_status_at_open_ck CHECK (
        status_at_open IN ('SUBMITTED', 'ACCEPTED', 'REJECTED')
    ),
    CONSTRAINT dispute_evidence_snapshot_type_ck CHECK (
        evidence_type IN ('DELIVERY_NOTE', 'INVOICE', 'VIDEO', 'PHOTO', 'SIGNED_DOCUMENT', 'OTHER')
    ),
    CONSTRAINT dispute_evidence_snapshot_media_type_ck CHECK (
        media_type IN (
            'application/pdf',
            'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
            'image/jpeg',
            'image/png',
            'video/mp4'
        )
    ),
    CONSTRAINT dispute_evidence_snapshot_version_ck CHECK (version_at_open >= 0),
    CONSTRAINT dispute_evidence_snapshot_size_ck CHECK (verified_size_bytes > 0),
    CONSTRAINT dispute_evidence_snapshot_sha_ck CHECK (verified_sha256 ~ '^[a-f0-9]{64}$'),
    CONSTRAINT dispute_evidence_snapshot_video_pair_ck CHECK (
        (video_job_id IS NULL AND video_result_id IS NULL)
        OR (video_job_id IS NOT NULL AND video_result_id IS NOT NULL)
    ),
    CONSTRAINT dispute_evidence_snapshot_lifecycle_ck CHECK (
        (status_at_open = 'SUBMITTED'
            AND submitted_at IS NOT NULL
            AND accepted_at IS NULL
            AND rejected_at IS NULL
            AND rejection_reason IS NULL)
        OR (status_at_open = 'ACCEPTED'
            AND submitted_at IS NOT NULL
            AND accepted_at IS NOT NULL
            AND rejected_at IS NULL
            AND rejection_reason IS NULL)
        OR (status_at_open = 'REJECTED'
            AND submitted_at IS NOT NULL
            AND accepted_at IS NULL
            AND rejected_at IS NOT NULL
            AND rejection_reason IS NOT NULL)
    )
);

CREATE UNIQUE INDEX dispute_evidence_snapshot_case_evidence_idx
    ON dispute_evidence_snapshot(dispute_case_id, evidence_submission_id);

CREATE TABLE dispute_comment (
    id UUID PRIMARY KEY,
    dispute_case_id UUID NOT NULL,
    deal_id UUID NOT NULL,
    body TEXT NOT NULL,
    author_tenant_id UUID NOT NULL,
    author_legal_entity_id UUID NOT NULL,
    author_user_id UUID NOT NULL,
    author_legal_name VARCHAR(200) NOT NULL,
    author_display_name VARCHAR(200) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT dispute_comment_case_deal_fk
        FOREIGN KEY (dispute_case_id, deal_id) REFERENCES dispute_case(id, deal_id),
    CONSTRAINT dispute_comment_author_user_fk
        FOREIGN KEY (author_user_id) REFERENCES identity_user(id),
    CONSTRAINT dispute_comment_author_user_tenant_fk
        FOREIGN KEY (author_user_id, author_tenant_id)
        REFERENCES tenant_user(user_id, tenant_id),
    CONSTRAINT dispute_comment_author_entity_tenant_fk
        FOREIGN KEY (author_legal_entity_id, author_tenant_id)
        REFERENCES legal_entity(id, tenant_id),
    CONSTRAINT dispute_comment_body_ck CHECK (
        body = btrim(body)
        AND char_length(body) BETWEEN 1 AND 4000
    ),
    CONSTRAINT dispute_comment_author_legal_name_ck CHECK (
        author_legal_name = btrim(author_legal_name)
        AND char_length(author_legal_name) BETWEEN 1 AND 200
    ),
    CONSTRAINT dispute_comment_author_display_name_ck CHECK (
        author_display_name = btrim(author_display_name)
        AND char_length(author_display_name) BETWEEN 1 AND 200
    )
);

CREATE INDEX dispute_comment_case_created_at_id_idx
    ON dispute_comment(dispute_case_id, created_at ASC, id ASC);

CREATE OR REPLACE FUNCTION dispute_case_mutation_guard()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'dispute case history is retained';
    END IF;

    IF TG_OP = 'INSERT' AND NEW.status = 'RESOLVED' THEN
        RAISE EXCEPTION 'RESOLVED disputes are unreachable in Slice 14A';
    END IF;

    IF TG_OP = 'UPDATE' THEN
        IF NEW.version < OLD.version OR NEW.version <> OLD.version + 1 THEN
            RAISE EXCEPTION 'dispute case version must increase monotonically by one';
        END IF;

        IF NEW.status = 'RESOLVED' THEN
            RAISE EXCEPTION 'RESOLVED disputes are unreachable in Slice 14A';
        END IF;

        IF OLD.status IS DISTINCT FROM NEW.status THEN
            IF OLD.status = 'OPEN' AND NEW.status = 'UNDER_REVIEW' THEN
                NULL;
            ELSIF OLD.status = 'OPEN' AND NEW.status = 'WITHDRAWN' THEN
                NULL;
            ELSIF OLD.status = 'UNDER_REVIEW' AND NEW.status = 'WITHDRAWN' THEN
                NULL;
            ELSE
                RAISE EXCEPTION 'invalid dispute case status transition';
            END IF;
        END IF;
    END IF;

    IF OLD.id IS DISTINCT FROM NEW.id
        OR OLD.deal_id IS DISTINCT FROM NEW.deal_id
        OR OLD.tenant_id IS DISTINCT FROM NEW.tenant_id
        OR OLD.fulfillment_id IS DISTINCT FROM NEW.fulfillment_id
        OR OLD.milestone_id IS DISTINCT FROM NEW.milestone_id
        OR OLD.ratification_package_id IS DISTINCT FROM NEW.ratification_package_id
        OR OLD.fulfillment_status_at_open IS DISTINCT FROM NEW.fulfillment_status_at_open
        OR OLD.fulfillment_version_at_open IS DISTINCT FROM NEW.fulfillment_version_at_open
        OR OLD.milestone_version_at_open IS DISTINCT FROM NEW.milestone_version_at_open
        OR OLD.reason_code IS DISTINCT FROM NEW.reason_code
        OR OLD.subject IS DISTINCT FROM NEW.subject
        OR OLD.statement IS DISTINCT FROM NEW.statement
        OR OLD.opening_tenant_id IS DISTINCT FROM NEW.opening_tenant_id
        OR OLD.opening_legal_entity_id IS DISTINCT FROM NEW.opening_legal_entity_id
        OR OLD.opening_user_id IS DISTINCT FROM NEW.opening_user_id
        OR OLD.opening_legal_name IS DISTINCT FROM NEW.opening_legal_name
        OR OLD.opened_at IS DISTINCT FROM NEW.opened_at
        OR OLD.created_at IS DISTINCT FROM NEW.created_at THEN
        RAISE EXCEPTION 'only dispute case lifecycle fields are mutable';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER dispute_case_mutation_guard
BEFORE UPDATE OR DELETE ON dispute_case
FOR EACH ROW
EXECUTE FUNCTION dispute_case_mutation_guard();

CREATE OR REPLACE FUNCTION dispute_evidence_snapshot_immutable()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'dispute evidence snapshots are immutable';
END;
$$;

CREATE TRIGGER dispute_evidence_snapshot_no_mutation
BEFORE UPDATE OR DELETE ON dispute_evidence_snapshot
FOR EACH ROW
EXECUTE FUNCTION dispute_evidence_snapshot_immutable();

CREATE OR REPLACE FUNCTION dispute_comment_immutable()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'dispute comments are append-only';
END;
$$;

CREATE TRIGGER dispute_comment_no_mutation
BEFORE UPDATE OR DELETE ON dispute_comment
FOR EACH ROW
EXECUTE FUNCTION dispute_comment_immutable();

CREATE OR REPLACE FUNCTION enforce_dispute_evidence_snapshot_same_deal()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    evidence_deal UUID;
    job_deal UUID;
    job_evidence UUID;
    result_job UUID;
    result_job_deal UUID;
BEGIN
    SELECT deal_id INTO evidence_deal
    FROM fulfillment_evidence_submission
    WHERE id = NEW.evidence_submission_id;

    IF evidence_deal IS NULL OR evidence_deal IS DISTINCT FROM NEW.deal_id THEN
        RAISE EXCEPTION 'dispute evidence snapshot must reference the same Deal as the case';
    END IF;

    IF NEW.video_job_id IS NOT NULL THEN
        SELECT deal_id, evidence_submission_id INTO job_deal, job_evidence
        FROM fulfillment_video_analysis_job
        WHERE id = NEW.video_job_id;

        IF job_deal IS NULL OR job_deal IS DISTINCT FROM NEW.deal_id THEN
            RAISE EXCEPTION 'dispute evidence snapshot video job must belong to the same Deal';
        END IF;

        IF job_evidence IS DISTINCT FROM NEW.evidence_submission_id THEN
            RAISE EXCEPTION 'dispute evidence snapshot video job must belong to the same evidence';
        END IF;

        SELECT job_id INTO result_job
        FROM fulfillment_video_analysis_result
        WHERE id = NEW.video_result_id;

        IF result_job IS NULL OR result_job IS DISTINCT FROM NEW.video_job_id THEN
            RAISE EXCEPTION 'dispute evidence snapshot video result must belong to the pinned job';
        END IF;

        SELECT deal_id INTO result_job_deal
        FROM fulfillment_video_analysis_job
        WHERE id = result_job;

        IF result_job_deal IS DISTINCT FROM NEW.deal_id THEN
            RAISE EXCEPTION 'dispute evidence snapshot video result must belong to the same Deal';
        END IF;
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER dispute_evidence_snapshot_same_deal
BEFORE INSERT ON dispute_evidence_snapshot
FOR EACH ROW
EXECUTE FUNCTION enforce_dispute_evidence_snapshot_same_deal();

CREATE OR REPLACE FUNCTION enforce_dispute_comment_same_deal()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    case_deal UUID;
BEGIN
    SELECT deal_id INTO case_deal
    FROM dispute_case
    WHERE id = NEW.dispute_case_id;

    IF case_deal IS NULL OR case_deal IS DISTINCT FROM NEW.deal_id THEN
        RAISE EXCEPTION 'dispute comment must reference the same Deal as the case';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER dispute_comment_same_deal
BEFORE INSERT ON dispute_comment
FOR EACH ROW
EXECUTE FUNCTION enforce_dispute_comment_same_deal();
