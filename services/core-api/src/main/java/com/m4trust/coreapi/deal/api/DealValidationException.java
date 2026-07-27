package com.m4trust.coreapi.deal.api;

import com.m4trust.coreapi.api.api.FieldErrorCode;
import com.m4trust.coreapi.api.api.FieldValidationError;
import com.m4trust.coreapi.deal.api.port.*;
import com.m4trust.coreapi.deal.domain.*;
import com.m4trust.coreapi.deal.infra.repository.*;
import java.util.List;

final class DealValidationException extends RuntimeException {

  private final List<FieldValidationError> errors;

  DealValidationException(String field, FieldErrorCode code, String message) {
    errors = List.of(new FieldValidationError(field, code, message));
  }

  List<FieldValidationError> errors() {
    return errors;
  }
}
