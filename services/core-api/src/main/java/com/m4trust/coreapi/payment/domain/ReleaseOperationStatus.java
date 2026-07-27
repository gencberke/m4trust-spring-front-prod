package com.m4trust.coreapi.payment.domain;

public enum ReleaseOperationStatus {
  QUEUED,
  PROCESSING,
  RECONCILIATION_REQUIRED,
  SIMULATED_SETTLED,
  SIMULATED_DECLINED,
  FAILED_BEFORE_DISPATCH;

  public boolean terminal() {
    return this == SIMULATED_SETTLED
        || this == SIMULATED_DECLINED
        || this == FAILED_BEFORE_DISPATCH;
  }
}
