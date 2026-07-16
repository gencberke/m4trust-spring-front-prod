CREATE TABLE identity_user (
    id UUID NOT NULL,
    email VARCHAR(320) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(200) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT identity_user_pk PRIMARY KEY (id),
    CONSTRAINT identity_user_email_normalized_ck
        CHECK (email = lower(btrim(email))),
    CONSTRAINT identity_user_display_name_trimmed_ck
        CHECK (display_name = btrim(display_name) AND display_name <> '')
);

CREATE UNIQUE INDEX identity_user_email_normalized_uk
    ON identity_user (lower(btrim(email)));
