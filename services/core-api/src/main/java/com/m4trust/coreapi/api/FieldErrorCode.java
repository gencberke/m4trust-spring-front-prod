package com.m4trust.coreapi.api;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Closed public field-validation codes. Exact-set must match OpenAPI FieldErrorCode.
 */
public enum FieldErrorCode {
    INVALID,
    INVALID_ENUM,
    INVALID_FORMAT,
    INVALID_LENGTH,
    INVALID_SORT,
    MUST_DIFFER,
    NOT_A_PARTICIPANT,
    OUT_OF_RANGE,
    PASSWORD_TOO_COMMON,
    REQUIRED;

    @JsonValue
    public String asString() {
        return name();
    }
}
