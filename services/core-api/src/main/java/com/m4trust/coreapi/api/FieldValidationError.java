package com.m4trust.coreapi.api;

/**
 * A single field validation error, per ADR-006 section 16 (field validation
 * errors array).
 */
public record FieldValidationError(String field, FieldErrorCode code, String message) {
}
