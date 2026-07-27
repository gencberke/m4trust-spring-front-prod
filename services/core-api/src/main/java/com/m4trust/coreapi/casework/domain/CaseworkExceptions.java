package com.m4trust.coreapi.casework.domain;

import com.m4trust.coreapi.api.api.ApiErrorCode;
import com.m4trust.coreapi.api.api.FieldErrorCode;
import com.m4trust.coreapi.api.api.FieldValidationError;
import java.util.List;

public class CaseworkExceptions {

  public static class MalformedRequest extends RuntimeException {
    private static final long serialVersionUID = 1L;
  }

  public static class NotFound extends RuntimeException {
    private static final long serialVersionUID = 1L;
  }

  public static class DisputeNotFound extends RuntimeException {
    private static final long serialVersionUID = 1L;
  }

  public static class OpenForbidden extends RuntimeException {
    private static final long serialVersionUID = 1L;
  }

  public static class CommentForbidden extends RuntimeException {
    private static final long serialVersionUID = 1L;
  }

  public static class AcknowledgeForbidden extends RuntimeException {
    private static final long serialVersionUID = 1L;
  }

  public static class WithdrawForbidden extends RuntimeException {
    private static final long serialVersionUID = 1L;
  }

  public static class Conflict extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final ApiErrorCode code;

    public Conflict(ApiErrorCode code) {
      super("Casework operation conflict: " + code.name());
      this.code = code;
    }

    public ApiErrorCode code() {
      return code;
    }
  }

  public static class Validation extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final List<FieldValidationError> errors;

    public Validation(String field, FieldErrorCode code, String message) {
      errors = List.of(new FieldValidationError(field, code, message));
    }

    public List<FieldValidationError> errors() {
      return errors;
    }
  }
}
