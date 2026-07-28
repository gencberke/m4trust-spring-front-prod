package com.m4trust.coreapi.casework.api;

import com.m4trust.coreapi.api.api.FieldErrorCode;
import com.m4trust.coreapi.api.api.FieldValidationError;
import java.util.List;

/** API-boundary exceptions that carry HTTP field-error transport details. */
final class CaseworkApiExceptions {

  private CaseworkApiExceptions() {}

  static final class Validation extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final List<FieldValidationError> errors;

    Validation(String field, FieldErrorCode code, String message) {
      errors = List.of(new FieldValidationError(field, code, message));
    }

    List<FieldValidationError> errors() {
      return errors;
    }
  }
}
