-- Slice 10: immutable canonical snapshot is deliberately separate from the mutable package wrapper.
CREATE TABLE ratification_package_snapshot (
    id UUID PRIMARY KEY,
    schema_version INTEGER NOT NULL CHECK (schema_version = 1),
    canonical_snapshot JSONB NOT NULL,
    content_hash CHAR(64) NOT NULL CHECK (content_hash ~ '^[a-f0-9]{64}$'),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ratification_package_snapshot_json_ck CHECK (jsonb_typeof(canonical_snapshot) = 'object')
);

CREATE TABLE ratification_package (
    id UUID PRIMARY KEY,
    deal_id UUID NOT NULL REFERENCES deal(id),
    snapshot_id UUID NOT NULL UNIQUE REFERENCES ratification_package_snapshot(id),
    version BIGINT NOT NULL DEFAULT 0 CHECK (version BETWEEN 0 AND 9007199254740991),
    status VARCHAR(16) NOT NULL CHECK (status IN ('PENDING', 'RATIFIED', 'REJECTED', 'SUPERSEDED')),
    buyer_legal_entity_id UUID NOT NULL,
    seller_legal_entity_id UUID NOT NULL,
    amount_minor BIGINT NOT NULL CHECK (amount_minor BETWEEN 1 AND 9007199254740991),
    currency CHAR(3) NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ratification_package_parties_distinct_ck CHECK (buyer_legal_entity_id <> seller_legal_entity_id),
    CONSTRAINT ratification_package_buyer_fk FOREIGN KEY (deal_id, buyer_legal_entity_id)
        REFERENCES deal_participant(deal_id, legal_entity_id),
    CONSTRAINT ratification_package_seller_fk FOREIGN KEY (deal_id, seller_legal_entity_id)
        REFERENCES deal_participant(deal_id, legal_entity_id),
    CONSTRAINT ratification_package_deal_id_uk UNIQUE (deal_id, id)
);
CREATE INDEX ratification_package_deal_created_idx ON ratification_package(deal_id, created_at, id);

ALTER TABLE deal ADD COLUMN current_ratification_package_id UUID;
ALTER TABLE deal ADD CONSTRAINT deal_current_ratification_package_fk
    FOREIGN KEY (id, current_ratification_package_id)
    REFERENCES ratification_package(deal_id, id);

-- Kept now so the approval slice starts from an append-only, one-effective-approval schema.
CREATE TABLE ratification_package_approval (
    id UUID PRIMARY KEY,
    package_id UUID NOT NULL REFERENCES ratification_package(id),
    legal_entity_id UUID NOT NULL,
    approved_by_user_id UUID NOT NULL REFERENCES identity_user(id),
    approved_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ratification_package_approval_package_entity_uk UNIQUE (package_id, legal_entity_id)
);

CREATE OR REPLACE FUNCTION ratification_snapshot_immutable()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
  RAISE EXCEPTION 'ratification snapshots are immutable and insert-only';
END; $$;
CREATE TRIGGER ratification_snapshot_no_mutation
  BEFORE UPDATE OR DELETE ON ratification_package_snapshot
  FOR EACH ROW EXECUTE FUNCTION ratification_snapshot_immutable();

CREATE OR REPLACE FUNCTION ratification_package_wrapper_guard()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
  IF TG_OP = 'DELETE' THEN
    RAISE EXCEPTION 'ratification package history is retained';
  END IF;
  IF OLD.id IS DISTINCT FROM NEW.id
      OR OLD.deal_id IS DISTINCT FROM NEW.deal_id
      OR OLD.snapshot_id IS DISTINCT FROM NEW.snapshot_id
      OR OLD.buyer_legal_entity_id IS DISTINCT FROM NEW.buyer_legal_entity_id
      OR OLD.seller_legal_entity_id IS DISTINCT FROM NEW.seller_legal_entity_id
      OR OLD.amount_minor IS DISTINCT FROM NEW.amount_minor
      OR OLD.currency IS DISTINCT FROM NEW.currency
      OR OLD.created_at IS DISTINCT FROM NEW.created_at THEN
    RAISE EXCEPTION 'only ratification package status and version are mutable';
  END IF;
  RETURN NEW;
END; $$;
CREATE TRIGGER ratification_package_wrapper_guard
  BEFORE UPDATE OR DELETE ON ratification_package
  FOR EACH ROW EXECUTE FUNCTION ratification_package_wrapper_guard();

CREATE OR REPLACE FUNCTION ratification_approval_immutable()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
  RAISE EXCEPTION 'ratification approvals are append-only';
END; $$;
CREATE TRIGGER ratification_approval_no_mutation
  BEFORE UPDATE OR DELETE ON ratification_package_approval
  FOR EACH ROW EXECUTE FUNCTION ratification_approval_immutable();

CREATE OR REPLACE FUNCTION ratification_approval_requires_package_party()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
  IF NOT EXISTS (
      SELECT 1 FROM ratification_package package
      WHERE package.id = NEW.package_id
        AND NEW.legal_entity_id IN (package.buyer_legal_entity_id, package.seller_legal_entity_id)
  ) THEN
    RAISE EXCEPTION 'ratification approval entity must be a package buyer or seller';
  END IF;
  RETURN NEW;
END; $$;
CREATE TRIGGER ratification_approval_party_only
  BEFORE INSERT ON ratification_package_approval
  FOR EACH ROW EXECUTE FUNCTION ratification_approval_requires_package_party();
