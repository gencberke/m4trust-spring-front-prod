-- Slice 11: provider-independent sandbox funding foundation (ADR-010 §2.2-§2.6).
-- FundingPlan amount/currency are immutable snapshots copied server-side from the
-- RATIFIED ratification package; V1 keeps exactly one FundingPlan per Deal and
-- exactly one FundingUnit per FundingPlan, both enforced by DB unique invariants
-- so a concurrent/idempotent create races safely to a single row (ADR-003 §21).

CREATE TABLE funding_plan (
    id UUID PRIMARY KEY,
    deal_id UUID NOT NULL UNIQUE REFERENCES deal(id),
    ratification_package_id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    amount_minor BIGINT NOT NULL CHECK (amount_minor BETWEEN 1 AND 9007199254740991),
    currency CHAR(3) NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    version BIGINT NOT NULL DEFAULT 0 CHECK (version BETWEEN 0 AND 9007199254740991),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT funding_plan_ratification_package_fk
        FOREIGN KEY (deal_id, ratification_package_id)
        REFERENCES ratification_package(deal_id, id)
);

CREATE TABLE funding_unit (
    id UUID PRIMARY KEY,
    funding_plan_id UUID NOT NULL REFERENCES funding_plan(id),
    sequence_no INTEGER NOT NULL CHECK (sequence_no = 1),
    amount_minor BIGINT NOT NULL CHECK (amount_minor BETWEEN 1 AND 9007199254740991),
    currency CHAR(3) NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    status VARCHAR(16) NOT NULL CHECK (status IN ('PLANNED', 'PENDING', 'FUNDED', 'FAILED')),
    version BIGINT NOT NULL DEFAULT 0 CHECK (version BETWEEN 0 AND 9007199254740991),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT funding_unit_plan_sequence_uk UNIQUE (funding_plan_id, sequence_no)
);
CREATE INDEX funding_unit_plan_idx ON funding_unit(funding_plan_id);

-- provider_key is the operation's lifetime-fixed provider idempotency key
-- (ADR-010 §2.4): retry always dispatches with the same key, never a new one.
CREATE TABLE payment_operation (
    id UUID PRIMARY KEY,
    funding_unit_id UUID NOT NULL REFERENCES funding_unit(id),
    provider_key UUID NOT NULL UNIQUE,
    status VARCHAR(16) NOT NULL CHECK (status IN ('CREATED', 'SUCCEEDED', 'DECLINED', 'UNCONFIRMED')),
    provider_reference VARCHAR(200),
    version BIGINT NOT NULL DEFAULT 0 CHECK (version BETWEEN 0 AND 9007199254740991),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX payment_operation_unit_idx ON payment_operation(funding_unit_id, created_at, id);

-- Durable dispatch record written in the same short transaction as the operation
-- intent/reconciliation request; the relay claims a row, closes the transaction,
-- and only then calls the provider port (ADR-010 §2.4).
CREATE TABLE payment_dispatch (
    id UUID PRIMARY KEY,
    payment_operation_id UUID NOT NULL REFERENCES payment_operation(id),
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
CREATE INDEX payment_dispatch_available_idx ON payment_dispatch(available_at) WHERE completed_at IS NULL;

CREATE OR REPLACE FUNCTION funding_plan_immutable()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
  IF TG_OP = 'DELETE' THEN
    RAISE EXCEPTION 'funding plans are retained';
  END IF;
  IF OLD.deal_id IS DISTINCT FROM NEW.deal_id
      OR OLD.ratification_package_id IS DISTINCT FROM NEW.ratification_package_id
      OR OLD.amount_minor IS DISTINCT FROM NEW.amount_minor
      OR OLD.currency IS DISTINCT FROM NEW.currency
      OR OLD.created_at IS DISTINCT FROM NEW.created_at THEN
    RAISE EXCEPTION 'funding plan amount/currency are immutable once created';
  END IF;
  RETURN NEW;
END; $$;
CREATE TRIGGER funding_plan_no_mutation
  BEFORE UPDATE OR DELETE ON funding_plan
  FOR EACH ROW EXECUTE FUNCTION funding_plan_immutable();
