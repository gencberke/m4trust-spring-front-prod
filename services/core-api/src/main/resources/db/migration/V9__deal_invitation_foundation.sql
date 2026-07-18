CREATE TABLE deal_invitation (
    id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    deal_id UUID NOT NULL,
    recipient_email TEXT NOT NULL,
    invitation_status TEXT NOT NULL,
    accepted_legal_entity_id UUID,
    accepted_legal_entity_tenant_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT deal_invitation_pk PRIMARY KEY (id),
    CONSTRAINT deal_invitation_deal_tenant_fk
        FOREIGN KEY (deal_id, tenant_id) REFERENCES deal (id, tenant_id),
    CONSTRAINT deal_invitation_accepted_entity_tenant_fk
        FOREIGN KEY (accepted_legal_entity_id, accepted_legal_entity_tenant_id)
        REFERENCES legal_entity (id, tenant_id),
    CONSTRAINT deal_invitation_recipient_email_ck CHECK (
        recipient_email = btrim(recipient_email)
        AND recipient_email = lower(recipient_email)
        AND char_length(recipient_email) BETWEEN 3 AND 320
    ),
    CONSTRAINT deal_invitation_status_ck CHECK (
        invitation_status IN ('PENDING', 'ACCEPTED', 'REJECTED', 'REVOKED')
    ),
    CONSTRAINT deal_invitation_accepted_entity_pair_ck CHECK (
        (accepted_legal_entity_id IS NULL AND accepted_legal_entity_tenant_id IS NULL)
        OR (accepted_legal_entity_id IS NOT NULL AND accepted_legal_entity_tenant_id IS NOT NULL)
    ),
    CONSTRAINT deal_invitation_accepted_state_ck CHECK (
        (invitation_status = 'ACCEPTED' AND accepted_legal_entity_id IS NOT NULL)
        OR (invitation_status <> 'ACCEPTED' AND accepted_legal_entity_id IS NULL)
    ),
    CONSTRAINT deal_invitation_version_ck CHECK (version >= 0),
    CONSTRAINT deal_invitation_timestamps_ck CHECK (updated_at >= created_at)
);

CREATE UNIQUE INDEX deal_invitation_pending_deal_recipient_uk
    ON deal_invitation (deal_id, recipient_email)
    WHERE invitation_status = 'PENDING';

CREATE INDEX deal_invitation_deal_created_at_id_idx
    ON deal_invitation (deal_id, created_at DESC, id DESC);

CREATE INDEX deal_invitation_pending_recipient_created_at_id_idx
    ON deal_invitation (recipient_email, created_at DESC, id DESC)
    WHERE invitation_status = 'PENDING';
