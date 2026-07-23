-- Slice 14B: Demo-scoped simulated settlement and release foundation (ADR-014 §2.3/§2.6).
-- Forward-only migration; V15-V22 remain frozen.

-- Immutable fulfillment completion instant required for dispute-window eligibility (ADR-014 §2.2).
ALTER TABLE fulfillment ADD COLUMN completed_at TIMESTAMPTZ;

UPDATE fulfillment f
SET completed_at = accepted.accepted_at
FROM (
    SELECT es.fulfillment_id, MAX(es.accepted_at) AS accepted_at, COUNT(*) AS accepted_count
    FROM fulfillment_evidence_submission es
    WHERE es.status = 'ACCEPTED' AND es.accepted_at IS NOT NULL
    GROUP BY es.fulfillment_id
) accepted
WHERE f.id = accepted.fulfillment_id
  AND f.status = 'COMPLETED'
  AND accepted.accepted_count = 1;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM fulfillment f
        WHERE f.status = 'COMPLETED' AND f.completed_at IS NULL
    ) THEN
        RAISE EXCEPTION 'COMPLETED fulfillment rows without a unique accepted evidence accepted_at cannot be backfilled';
    END IF;
END $$;

ALTER TABLE fulfillment ADD CONSTRAINT fulfillment_completed_at_ck CHECK (
    (status = 'COMPLETED' AND completed_at IS NOT NULL AND completed_at >= created_at)
    OR (status <> 'COMPLETED' AND completed_at IS NULL)
);

CREATE TABLE settlement (
    id UUID PRIMARY KEY,
    deal_id UUID NOT NULL UNIQUE REFERENCES deal(id),
    funding_unit_id UUID NOT NULL REFERENCES funding_unit(id),
    tenant_id UUID NOT NULL,
    status VARCHAR(32) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0 CHECK (version BETWEEN 0 AND 9007199254740991),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT settlement_deal_hosting_tenant_fk
        FOREIGN KEY (deal_id, tenant_id) REFERENCES deal(id, tenant_id),
    CONSTRAINT settlement_status_ck CHECK (
        status IN ('NOT_READY', 'READY', 'PROCESSING', 'ON_HOLD', 'SIMULATED_SETTLED', 'FAILED')
    ),
    CONSTRAINT settlement_timestamps_ck CHECK (updated_at >= created_at)
);

CREATE INDEX settlement_deal_idx ON settlement(deal_id);

CREATE TABLE release_operation (
    id UUID PRIMARY KEY,
    settlement_id UUID NOT NULL UNIQUE REFERENCES settlement(id),
    provider_key UUID NOT NULL UNIQUE,
    status VARCHAR(32) NOT NULL,
    provider_reference VARCHAR(200),
    version BIGINT NOT NULL DEFAULT 0 CHECK (version BETWEEN 0 AND 9007199254740991),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT release_operation_status_ck CHECK (
        status IN (
            'QUEUED', 'PROCESSING', 'RECONCILIATION_REQUIRED',
            'SIMULATED_SETTLED', 'SIMULATED_DECLINED', 'FAILED_BEFORE_DISPATCH'
        )
    ),
    CONSTRAINT release_operation_timestamps_ck CHECK (updated_at >= created_at)
);

CREATE INDEX release_operation_settlement_idx ON release_operation(settlement_id);

CREATE TABLE release_dispatch (
    id UUID PRIMARY KEY,
    release_operation_id UUID NOT NULL REFERENCES release_operation(id),
    dispatch_type VARCHAR(16) NOT NULL CHECK (dispatch_type IN ('INITIATE', 'RECONCILE')),
    provider_key UUID NOT NULL,
    amount_minor BIGINT NOT NULL CHECK (amount_minor > 0),
    currency CHAR(3) NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    completed_at TIMESTAMPTZ,
    claimed_at TIMESTAMPTZ,
    claim_token UUID,
    created_at TIMESTAMPTZ NOT NULL,
    available_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX release_dispatch_available_idx ON release_dispatch(available_at) WHERE completed_at IS NULL;

CREATE OR REPLACE FUNCTION settlement_status_transition_guard()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'settlement rows are retained';
    END IF;
    IF OLD.deal_id IS DISTINCT FROM NEW.deal_id
        OR OLD.funding_unit_id IS DISTINCT FROM NEW.funding_unit_id
        OR OLD.tenant_id IS DISTINCT FROM NEW.tenant_id
        OR OLD.created_at IS DISTINCT FROM NEW.created_at THEN
        RAISE EXCEPTION 'settlement identity fields are immutable';
    END IF;
    IF NEW.version <> OLD.version + 1 THEN
        RAISE EXCEPTION 'settlement version must increment by exactly one';
    END IF;
  RETURN NEW;
END; $$;

CREATE TRIGGER settlement_status_transition_guard_trg
    BEFORE UPDATE ON settlement
    FOR EACH ROW EXECUTE FUNCTION settlement_status_transition_guard();

CREATE OR REPLACE FUNCTION release_operation_immutable_history()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'release operation history is retained';
    END IF;
    IF OLD.settlement_id IS DISTINCT FROM NEW.settlement_id
        OR OLD.provider_key IS DISTINCT FROM NEW.provider_key
        OR OLD.created_at IS DISTINCT FROM NEW.created_at THEN
        RAISE EXCEPTION 'release operation identity fields are immutable';
    END IF;
    IF NEW.version <> OLD.version + 1 THEN
        RAISE EXCEPTION 'release operation version must increment by exactly one';
    END IF;
    RETURN NEW;
END; $$;

CREATE TRIGGER release_operation_immutable_history_trg
    BEFORE UPDATE ON release_operation
    FOR EACH ROW EXECUTE FUNCTION release_operation_immutable_history();
