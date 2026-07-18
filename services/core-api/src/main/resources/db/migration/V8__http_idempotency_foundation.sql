CREATE TABLE http_idempotency_record (
    id UUID NOT NULL,
    actor_user_id UUID NOT NULL,
    actor_tenant_id UUID NOT NULL,
    operation TEXT NOT NULL,
    idempotency_key UUID NOT NULL,
    canonical_request_hash CHAR(64) NOT NULL,
    result_type TEXT,
    result_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT http_idempotency_record_pk PRIMARY KEY (id),
    CONSTRAINT http_idempotency_record_actor_tenant_fk
        FOREIGN KEY (actor_user_id, actor_tenant_id)
        REFERENCES tenant_user (user_id, tenant_id),
    CONSTRAINT http_idempotency_record_actor_tenant_operation_key_uk
        UNIQUE (actor_user_id, actor_tenant_id, operation, idempotency_key),
    CONSTRAINT http_idempotency_record_operation_ck
        CHECK (
            operation = btrim(operation)
            AND char_length(operation) BETWEEN 1 AND 100
        ),
    CONSTRAINT http_idempotency_record_request_hash_ck
        CHECK (canonical_request_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT http_idempotency_record_result_reference_ck
        CHECK (
            (result_type IS NULL AND result_id IS NULL)
            OR (
                result_type = btrim(result_type)
                AND char_length(result_type) BETWEEN 1 AND 100
                AND result_id IS NOT NULL
            )
        )
);
