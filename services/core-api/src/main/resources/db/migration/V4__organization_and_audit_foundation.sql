CREATE TABLE tenant (
    id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT tenant_pk PRIMARY KEY (id),
    CONSTRAINT tenant_version_ck CHECK (version >= 0),
    CONSTRAINT tenant_timestamps_ck CHECK (updated_at >= created_at)
);

CREATE TABLE tenant_user (
    user_id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT tenant_user_pk PRIMARY KEY (user_id),
    CONSTRAINT tenant_user_user_tenant_uk UNIQUE (user_id, tenant_id),
    CONSTRAINT tenant_user_user_fk
        FOREIGN KEY (user_id) REFERENCES identity_user (id),
    CONSTRAINT tenant_user_tenant_fk
        FOREIGN KEY (tenant_id) REFERENCES tenant (id),
    CONSTRAINT tenant_user_version_ck CHECK (version >= 0),
    CONSTRAINT tenant_user_timestamps_ck CHECK (updated_at >= created_at)
);

CREATE INDEX tenant_user_tenant_id_idx ON tenant_user (tenant_id);

CREATE TABLE legal_entity (
    id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    legal_name TEXT NOT NULL,
    registration_number TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT legal_entity_pk PRIMARY KEY (id),
    CONSTRAINT legal_entity_id_tenant_uk UNIQUE (id, tenant_id),
    CONSTRAINT legal_entity_tenant_fk
        FOREIGN KEY (tenant_id) REFERENCES tenant (id),
    CONSTRAINT legal_entity_legal_name_ck
        CHECK (
            legal_name = btrim(legal_name)
            AND char_length(legal_name) BETWEEN 1 AND 200
        ),
    CONSTRAINT legal_entity_registration_number_ck
        CHECK (
            registration_number = btrim(registration_number)
            AND char_length(registration_number) BETWEEN 1 AND 100
        ),
    CONSTRAINT legal_entity_version_ck CHECK (version >= 0),
    CONSTRAINT legal_entity_timestamps_ck CHECK (updated_at >= created_at)
);

CREATE INDEX legal_entity_tenant_id_idx ON legal_entity (tenant_id);

CREATE TABLE legal_entity_membership (
    id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    legal_entity_id UUID NOT NULL,
    user_id UUID NOT NULL,
    role TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT legal_entity_membership_pk PRIMARY KEY (id),
    CONSTRAINT legal_entity_membership_entity_user_uk
        UNIQUE (legal_entity_id, user_id),
    CONSTRAINT legal_entity_membership_entity_tenant_fk
        FOREIGN KEY (legal_entity_id, tenant_id)
        REFERENCES legal_entity (id, tenant_id),
    CONSTRAINT legal_entity_membership_user_tenant_fk
        FOREIGN KEY (user_id, tenant_id)
        REFERENCES tenant_user (user_id, tenant_id),
    CONSTRAINT legal_entity_membership_role_ck
        CHECK (role IN ('ADMIN', 'MEMBER')),
    CONSTRAINT legal_entity_membership_version_ck CHECK (version >= 0),
    CONSTRAINT legal_entity_membership_timestamps_ck
        CHECK (updated_at >= created_at)
);

CREATE INDEX legal_entity_membership_user_tenant_idx
    ON legal_entity_membership (user_id, tenant_id);
CREATE INDEX legal_entity_membership_entity_tenant_idx
    ON legal_entity_membership (legal_entity_id, tenant_id);

CREATE TABLE audit_record (
    id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    actor_user_id UUID,
    legal_entity_id UUID,
    subject_type TEXT NOT NULL,
    subject_id UUID NOT NULL,
    action TEXT NOT NULL,
    correlation_id UUID NOT NULL,
    causation_id UUID,
    occurred_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT audit_record_pk PRIMARY KEY (id),
    CONSTRAINT audit_record_tenant_fk
        FOREIGN KEY (tenant_id) REFERENCES tenant (id),
    CONSTRAINT audit_record_actor_tenant_fk
        FOREIGN KEY (actor_user_id, tenant_id)
        REFERENCES tenant_user (user_id, tenant_id),
    CONSTRAINT audit_record_legal_entity_tenant_fk
        FOREIGN KEY (legal_entity_id, tenant_id)
        REFERENCES legal_entity (id, tenant_id),
    CONSTRAINT audit_record_subject_type_ck
        CHECK (
            subject_type = btrim(subject_type)
            AND char_length(subject_type) BETWEEN 1 AND 100
        ),
    CONSTRAINT audit_record_action_ck
        CHECK (
            action = btrim(action)
            AND char_length(action) BETWEEN 1 AND 100
        ),
    CONSTRAINT audit_record_version_ck CHECK (version = 0)
);

CREATE INDEX audit_record_tenant_occurred_at_idx
    ON audit_record (tenant_id, occurred_at);
CREATE INDEX audit_record_actor_user_id_idx
    ON audit_record (actor_user_id);
CREATE INDEX audit_record_legal_entity_id_idx
    ON audit_record (legal_entity_id);

CREATE FUNCTION reject_audit_record_mutation()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'audit_record is append-only';
END;
$$;

CREATE TRIGGER audit_record_append_only
BEFORE UPDATE OR DELETE ON audit_record
FOR EACH ROW
EXECUTE FUNCTION reject_audit_record_mutation();

CREATE TEMPORARY TABLE tenant_backfill (
    user_id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT tenant_backfill_pk PRIMARY KEY (user_id)
) ON COMMIT DROP;

INSERT INTO tenant_backfill (user_id, tenant_id, created_at)
SELECT identity_user.id, gen_random_uuid(), identity_user.created_at
FROM identity_user;

INSERT INTO tenant (id, created_at, updated_at)
SELECT tenant_id, created_at, created_at
FROM tenant_backfill;

INSERT INTO tenant_user (user_id, tenant_id, created_at, updated_at)
SELECT user_id, tenant_id, created_at, created_at
FROM tenant_backfill;
