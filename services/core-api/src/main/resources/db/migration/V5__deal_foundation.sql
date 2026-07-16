CREATE SEQUENCE deal_reference_sequence
    AS BIGINT
    START WITH 1
    INCREMENT BY 1
    MINVALUE 1
    MAXVALUE 9999999999
    NO CYCLE;

CREATE TABLE deal (
    id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    reference TEXT NOT NULL,
    title TEXT NOT NULL,
    description TEXT,
    deal_status TEXT NOT NULL,
    initiator_legal_entity_id UUID NOT NULL,
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT deal_pk PRIMARY KEY (id),
    CONSTRAINT deal_id_tenant_uk UNIQUE (id, tenant_id),
    CONSTRAINT deal_reference_uk UNIQUE (reference),
    CONSTRAINT deal_tenant_fk
        FOREIGN KEY (tenant_id) REFERENCES tenant (id),
    CONSTRAINT deal_initiator_tenant_fk
        FOREIGN KEY (initiator_legal_entity_id, tenant_id)
        REFERENCES legal_entity (id, tenant_id),
    CONSTRAINT deal_creator_tenant_fk
        FOREIGN KEY (created_by, tenant_id)
        REFERENCES tenant_user (user_id, tenant_id),
    CONSTRAINT deal_reference_ck
        CHECK (reference ~ '^DL-[0-9]{10}$'),
    CONSTRAINT deal_title_ck
        CHECK (
            title = btrim(title)
            AND char_length(title) BETWEEN 1 AND 200
        ),
    CONSTRAINT deal_description_ck
        CHECK (
            description IS NULL
            OR char_length(description) <= 4000
        ),
    CONSTRAINT deal_status_ck
        CHECK (
            deal_status IN (
                'DRAFT',
                'ACTIVE',
                'CANCELLED',
                'COMPLETED',
                'ARCHIVED'
            )
        ),
    CONSTRAINT deal_version_ck CHECK (version >= 0),
    CONSTRAINT deal_timestamps_ck CHECK (updated_at >= created_at)
);

CREATE INDEX deal_tenant_created_at_id_idx
    ON deal (tenant_id, created_at DESC, id DESC);
CREATE INDEX deal_tenant_status_created_at_id_idx
    ON deal (tenant_id, deal_status, created_at DESC, id DESC);
CREATE INDEX deal_tenant_title_id_idx
    ON deal (tenant_id, lower(title), id);
CREATE INDEX deal_tenant_status_title_id_idx
    ON deal (tenant_id, deal_status, lower(title), id);

CREATE TABLE deal_participant (
    deal_id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    legal_entity_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT deal_participant_pk
        PRIMARY KEY (deal_id, legal_entity_id),
    CONSTRAINT deal_participant_deal_tenant_fk
        FOREIGN KEY (deal_id, tenant_id)
        REFERENCES deal (id, tenant_id),
    CONSTRAINT deal_participant_entity_tenant_fk
        FOREIGN KEY (legal_entity_id, tenant_id)
        REFERENCES legal_entity (id, tenant_id)
);

CREATE INDEX deal_participant_entity_tenant_deal_idx
    ON deal_participant (legal_entity_id, tenant_id, deal_id);
