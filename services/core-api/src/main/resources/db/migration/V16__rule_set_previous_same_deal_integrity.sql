-- A predecessor is historical metadata, but when present it must belong to the
-- same Deal.  V15 intentionally remains untouched after application.
ALTER TABLE contract_intelligence_rule_set_version
    ADD COLUMN previous_rule_set_deal_id UUID,
    ADD CONSTRAINT contract_intelligence_rule_set_previous_pair_ck CHECK (
        (previous_rule_set_version_id IS NULL AND previous_rule_set_deal_id IS NULL)
        OR (previous_rule_set_version_id IS NOT NULL AND previous_rule_set_deal_id = deal_id)
    ),
    ADD CONSTRAINT contract_intelligence_rule_set_previous_same_deal_fk
        FOREIGN KEY (previous_rule_set_deal_id, previous_rule_set_version_id)
        REFERENCES contract_intelligence_rule_set_version(deal_id, id);
