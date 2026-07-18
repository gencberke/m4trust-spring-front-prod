CREATE TABLE document (
    id UUID NOT NULL,
    deal_id UUID NOT NULL,
    file_name TEXT NOT NULL,
    media_type TEXT NOT NULL,
    document_status TEXT NOT NULL,
    object_key TEXT NOT NULL,
    declared_size_bytes BIGINT NOT NULL,
    declared_sha256 CHAR(64) NOT NULL,
    upload_expires_at TIMESTAMPTZ NOT NULL,
    verified_size_bytes BIGINT,
    verified_sha256 CHAR(64),
    object_version TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    available_at TIMESTAMPTZ,
    superseded_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT document_pk PRIMARY KEY (id),
    CONSTRAINT document_deal_fk FOREIGN KEY (deal_id) REFERENCES deal (id),
    CONSTRAINT document_deal_id_status_uk UNIQUE (deal_id, id, document_status),
    CONSTRAINT document_object_key_uk UNIQUE (object_key),
    CONSTRAINT document_file_name_ck CHECK (
        file_name = btrim(file_name)
        AND char_length(file_name) BETWEEN 1 AND 255
    ),
    CONSTRAINT document_media_type_ck CHECK (
        media_type IN (
            'application/pdf',
            'application/vnd.openxmlformats-officedocument.wordprocessingml.document'
        )
    ),
    CONSTRAINT document_status_ck CHECK (
        document_status IN ('PENDING_UPLOAD', 'AVAILABLE', 'SUPERSEDED')
    ),
    CONSTRAINT document_object_key_ck CHECK (
        object_key = btrim(object_key)
        AND char_length(object_key) BETWEEN 1 AND 1024
    ),
    CONSTRAINT document_declared_size_bytes_ck CHECK (declared_size_bytes > 0),
    CONSTRAINT document_declared_sha256_ck CHECK (
        declared_sha256 ~ '^[0-9a-f]{64}$'
    ),
    CONSTRAINT document_verified_size_bytes_ck CHECK (
        verified_size_bytes IS NULL OR verified_size_bytes > 0
    ),
    CONSTRAINT document_verified_sha256_ck CHECK (
        verified_sha256 IS NULL OR verified_sha256 ~ '^[0-9a-f]{64}$'
    ),
    CONSTRAINT document_object_version_ck CHECK (
        object_version IS NULL
        OR (object_version = btrim(object_version) AND char_length(object_version) BETWEEN 1 AND 1024)
    ),
    CONSTRAINT document_lifecycle_data_ck CHECK (
        (document_status = 'PENDING_UPLOAD'
            AND verified_size_bytes IS NULL
            AND verified_sha256 IS NULL
            AND object_version IS NULL
            AND available_at IS NULL
            AND superseded_at IS NULL)
        OR (document_status = 'AVAILABLE'
            AND verified_size_bytes IS NOT NULL
            AND verified_sha256 IS NOT NULL
            AND object_version IS NOT NULL
            AND available_at IS NOT NULL
            AND superseded_at IS NULL)
        OR (document_status = 'SUPERSEDED'
            AND verified_size_bytes IS NOT NULL
            AND verified_sha256 IS NOT NULL
            AND object_version IS NOT NULL
            AND available_at IS NOT NULL
            AND superseded_at IS NOT NULL)
    ),
    CONSTRAINT document_timestamps_ck CHECK (
        upload_expires_at > created_at
        AND updated_at >= created_at
        AND (available_at IS NULL OR available_at >= created_at)
        AND (superseded_at IS NULL OR superseded_at >= available_at)
    ),
    CONSTRAINT document_version_ck CHECK (version >= 0)
);

CREATE INDEX document_deal_created_at_id_idx
    ON document (deal_id, created_at DESC, id DESC);

ALTER TABLE deal
    ADD COLUMN current_document_id UUID,
    ADD COLUMN current_document_status TEXT,
    ADD CONSTRAINT deal_current_document_pair_ck CHECK (
        (current_document_id IS NULL AND current_document_status IS NULL)
        OR (current_document_id IS NOT NULL AND current_document_status = 'AVAILABLE')
    ),
    ADD CONSTRAINT deal_current_available_document_fk
        FOREIGN KEY (id, current_document_id, current_document_status)
        REFERENCES document (deal_id, id, document_status);

CREATE FUNCTION document_enforce_lifecycle()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF OLD.document_status = 'PENDING_UPLOAD' THEN
        IF NEW.document_status NOT IN ('PENDING_UPLOAD', 'AVAILABLE') THEN
            RAISE EXCEPTION 'document pending upload may only remain pending or become available';
        END IF;
    ELSIF OLD.document_status = 'AVAILABLE' THEN
        IF NEW.document_status NOT IN ('AVAILABLE', 'SUPERSEDED') THEN
            RAISE EXCEPTION 'available document may only remain available or become superseded';
        END IF;
        IF NEW.object_key IS DISTINCT FROM OLD.object_key
                OR NEW.verified_size_bytes IS DISTINCT FROM OLD.verified_size_bytes
                OR NEW.verified_sha256 IS DISTINCT FROM OLD.verified_sha256
                OR NEW.object_version IS DISTINCT FROM OLD.object_version THEN
            RAISE EXCEPTION 'finalized document object metadata is immutable';
        END IF;
    ELSE
        IF NEW.document_status <> 'SUPERSEDED' THEN
            RAISE EXCEPTION 'superseded document is terminal';
        END IF;
        IF NEW.object_key IS DISTINCT FROM OLD.object_key
                OR NEW.verified_size_bytes IS DISTINCT FROM OLD.verified_size_bytes
                OR NEW.verified_sha256 IS DISTINCT FROM OLD.verified_sha256
                OR NEW.object_version IS DISTINCT FROM OLD.object_version THEN
            RAISE EXCEPTION 'finalized document object metadata is immutable';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER document_lifecycle_trg
    BEFORE UPDATE ON document
    FOR EACH ROW
    EXECUTE FUNCTION document_enforce_lifecycle();
