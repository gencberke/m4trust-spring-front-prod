CREATE TABLE integration_outbox_event (
    id UUID NOT NULL,
    event_type TEXT NOT NULL,
    exchange_name TEXT NOT NULL,
    routing_key TEXT NOT NULL,
    payload JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    available_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    claimed_at TIMESTAMPTZ,
    claim_token UUID,
    published_at TIMESTAMPTZ,
    publish_attempt_count INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT integration_outbox_event_pk PRIMARY KEY (id),
    CONSTRAINT integration_outbox_event_type_ck CHECK (
        event_type = btrim(event_type) AND char_length(event_type) BETWEEN 1 AND 200
    ),
    CONSTRAINT integration_outbox_exchange_name_ck CHECK (
        exchange_name = btrim(exchange_name) AND char_length(exchange_name) BETWEEN 1 AND 200
    ),
    CONSTRAINT integration_outbox_routing_key_ck CHECK (
        routing_key = btrim(routing_key) AND char_length(routing_key) BETWEEN 1 AND 200
    ),
    CONSTRAINT integration_outbox_payload_object_ck CHECK (jsonb_typeof(payload) = 'object'),
    CONSTRAINT integration_outbox_claim_pair_ck CHECK (
        (claimed_at IS NULL AND claim_token IS NULL) OR (claimed_at IS NOT NULL AND claim_token IS NOT NULL)
    ),
    CONSTRAINT integration_outbox_timestamps_ck CHECK (
        available_at >= created_at AND (claimed_at IS NULL OR claimed_at >= created_at)
            AND (published_at IS NULL OR published_at >= created_at)
    ),
    CONSTRAINT integration_outbox_attempt_count_ck CHECK (publish_attempt_count >= 0)
);

CREATE INDEX integration_outbox_event_pending_idx
    ON integration_outbox_event (available_at, created_at)
    WHERE published_at IS NULL;

CREATE INDEX integration_outbox_event_claimed_idx
    ON integration_outbox_event (claimed_at)
    WHERE published_at IS NULL AND claimed_at IS NOT NULL;

CREATE TABLE integration_inbox_event (
    event_id UUID NOT NULL,
    event_type TEXT NOT NULL,
    received_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT integration_inbox_event_pk PRIMARY KEY (event_id),
    CONSTRAINT integration_inbox_event_type_ck CHECK (
        event_type = btrim(event_type) AND char_length(event_type) BETWEEN 1 AND 200
    )
);

CREATE INDEX integration_inbox_event_received_at_idx
    ON integration_inbox_event (received_at);
