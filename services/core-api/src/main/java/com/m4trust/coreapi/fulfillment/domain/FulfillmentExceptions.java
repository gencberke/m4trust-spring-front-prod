package com.m4trust.coreapi.fulfillment.domain;

public final class FulfillmentExceptions {
  private FulfillmentExceptions() {}

  public static class FulfillmentNotFound extends RuntimeException {}

  public static class EvidenceNotFound extends RuntimeException {}

  public static class DealNotFound extends RuntimeException {}

  public static class StartForbidden extends RuntimeException {}

  public static class UploadForbidden extends RuntimeException {}

  public static class ReviewForbidden extends RuntimeException {}

  public static class RequestForbidden extends RuntimeException {}

  public static class DownloadNotAvailable extends RuntimeException {}

  public static class MalformedRequest extends RuntimeException {}

  public static class Conflict extends RuntimeException {
    private final FulfillmentErrorCode code;

    public Conflict(FulfillmentErrorCode code) {
      super("Fulfillment operation conflict: " + code.name());
      this.code = code;
    }

    public FulfillmentErrorCode code() {
      return code;
    }
  }

  public static class Validation extends RuntimeException {
    private final String field;

    public Validation(String field) {
      this.field = field;
    }

    public String field() {
      return field;
    }
  }
}
