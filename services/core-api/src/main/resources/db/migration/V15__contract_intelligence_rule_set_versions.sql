-- Slice 9: accepted rule sets are append-only historical evidence.  Application
-- repositories intentionally expose INSERT/read operations only.
CREATE TABLE contract_intelligence_rule_set_version (
    id UUID PRIMARY KEY,
    deal_id UUID NOT NULL REFERENCES deal(id),
    version BIGINT NOT NULL,
    source_analysis_id UUID NOT NULL UNIQUE REFERENCES contract_intelligence_analysis_job(id),
    source_extraction_result_version_id UUID NOT NULL REFERENCES contract_intelligence_extraction_result_version(id),
    created_by_user_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    previous_rule_set_version_id UUID REFERENCES contract_intelligence_rule_set_version(id),
    rules JSONB NOT NULL,
    excluded_rule_references JSONB NOT NULL,
    CONSTRAINT contract_intelligence_rule_set_version_deal_version_uk UNIQUE (deal_id, version),
    CONSTRAINT contract_intelligence_rule_set_version_deal_id_uk UNIQUE (deal_id, id),
    CONSTRAINT contract_intelligence_rule_set_version_rules_ck CHECK (jsonb_typeof(rules) = 'array'),
    CONSTRAINT contract_intelligence_rule_set_version_excluded_ck CHECK (jsonb_typeof(excluded_rule_references) = 'array')
);

ALTER TABLE deal
    ADD COLUMN current_rule_set_version_id UUID,
    ADD CONSTRAINT deal_current_rule_set_fk
       FOREIGN KEY (id, current_rule_set_version_id)
       REFERENCES contract_intelligence_rule_set_version(deal_id, id);

CREATE OR REPLACE FUNCTION contract_intelligence_rule_set_version_immutable()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
  RAISE EXCEPTION 'rule set versions are immutable and insert-only';
END; $$;
CREATE TRIGGER contract_intelligence_rule_set_version_no_update
  BEFORE UPDATE OR DELETE ON contract_intelligence_rule_set_version
  FOR EACH ROW EXECUTE FUNCTION contract_intelligence_rule_set_version_immutable();
