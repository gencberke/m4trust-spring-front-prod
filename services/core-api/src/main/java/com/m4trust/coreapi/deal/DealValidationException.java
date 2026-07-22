package com.m4trust.coreapi.deal;

import java.util.List;

import com.m4trust.coreapi.api.FieldErrorCode;
import com.m4trust.coreapi.api.FieldValidationError;

final class DealValidationException extends RuntimeException {

    private final List<FieldValidationError> errors;

    DealValidationException(String field, FieldErrorCode code, String message) {
        errors = List.of(new FieldValidationError(field, code, message));
    }

    List<FieldValidationError> errors() {
        return errors;
    }
}
