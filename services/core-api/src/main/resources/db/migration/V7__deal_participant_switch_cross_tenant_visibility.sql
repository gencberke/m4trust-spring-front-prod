UPDATE deal_participant participant
SET legal_entity_tenant_id = deal.tenant_id
FROM deal
WHERE participant.deal_id = deal.id
  AND participant.legal_entity_tenant_id IS NULL;

ALTER TABLE deal_participant
    ALTER COLUMN legal_entity_tenant_id SET NOT NULL;

ALTER TABLE deal_participant
    DROP CONSTRAINT deal_participant_entity_tenant_fk;
