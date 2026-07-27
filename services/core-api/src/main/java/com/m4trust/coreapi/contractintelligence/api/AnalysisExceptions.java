package com.m4trust.coreapi.contractintelligence.api;

import com.m4trust.coreapi.api.api.ApiErrorCode;
import com.m4trust.coreapi.contractintelligence.api.dto.*;
import com.m4trust.coreapi.contractintelligence.domain.*;
import com.m4trust.coreapi.contractintelligence.infra.*;

public final class AnalysisExceptions {

  private AnalysisExceptions() {}

  public static final class DealNotFound extends RuntimeException {}

  public static final class MalformedRequest extends RuntimeException {}

  public static final class RequestForbidden extends RuntimeException {}

  public static final class ReviewAcceptanceForbidden extends RuntimeException {}

  public static final class RuleSetVersionNotFound extends RuntimeException {}

  public static final class Conflict extends RuntimeException {
    private final ApiErrorCode code;

    public Conflict(ApiErrorCode code) {
      this.code = code;
    }

    public ApiErrorCode code() {
      return code;
    }
  }

  public static final class Validation extends RuntimeException {
    private final String field;

    public Validation(String field) {
      this.field = field;
    }

    public String field() {
      return field;
    }
  }
}
