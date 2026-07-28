package com.m4trust.coreapi.api.api;

/** A single field validation error in the RFC 9457 field-errors array. */
public record FieldValidationError(String field, FieldErrorCode code, String message) {}
