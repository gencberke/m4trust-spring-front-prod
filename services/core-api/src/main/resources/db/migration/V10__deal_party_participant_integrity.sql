ALTER TABLE deal
    ADD COLUMN buyer_legal_entity_id UUID,
    ADD COLUMN seller_legal_entity_id UUID,
    ADD CONSTRAINT deal_buyer_participant_fk
        FOREIGN KEY (id, buyer_legal_entity_id)
        REFERENCES deal_participant (deal_id, legal_entity_id),
    ADD CONSTRAINT deal_seller_participant_fk
        FOREIGN KEY (id, seller_legal_entity_id)
        REFERENCES deal_participant (deal_id, legal_entity_id),
    ADD CONSTRAINT deal_buyer_seller_distinct_ck
        CHECK (
            buyer_legal_entity_id IS NULL
            OR seller_legal_entity_id IS NULL
            OR buyer_legal_entity_id <> seller_legal_entity_id
        );
