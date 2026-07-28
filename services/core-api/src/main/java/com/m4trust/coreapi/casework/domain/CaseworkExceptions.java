package com.m4trust.coreapi.casework.domain;

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

    private final CaseworkConflictReason reason;

    public Conflict(CaseworkConflictReason reason) {
      super("Casework operation conflict: " + reason.name());
      this.reason = reason;
    }

    public CaseworkConflictReason reason() {
      return reason;
    }
  }
}
