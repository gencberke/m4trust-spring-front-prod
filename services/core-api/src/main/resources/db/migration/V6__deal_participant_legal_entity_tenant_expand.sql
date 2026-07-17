ALTER TABLE deal_participant
    ADD COLUMN legal_entity_tenant_id UUID;

UPDATE deal_participant
SET legal_entity_tenant_id = tenant_id
WHERE legal_entity_tenant_id IS NULL;

ALTER TABLE deal_participant
    ADD CONSTRAINT deal_participant_entity_legal_tenant_fk
        FOREIGN KEY (legal_entity_id, legal_entity_tenant_id)
        REFERENCES legal_entity (id, tenant_id);

CREATE INDEX deal_participant_entity_legal_tenant_deal_idx
    ON deal_participant (
        legal_entity_id,
        legal_entity_tenant_id,
        deal_id
    );
