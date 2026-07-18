UPDATE deal_participant
SET legal_entity_tenant_id = tenant_id
WHERE legal_entity_tenant_id IS NULL;

ALTER TABLE deal_participant
    ALTER COLUMN legal_entity_tenant_id SET NOT NULL;

ALTER TABLE deal_participant
    DROP CONSTRAINT deal_participant_entity_tenant_fk;
