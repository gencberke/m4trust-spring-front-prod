package com.m4trust.coreapi.payment.domain;

public enum SettlementStatus {
  NOT_READY,
  READY,
  PROCESSING,
  ON_HOLD,
  SIMULATED_SETTLED,
  FAILED;

  public boolean terminal() {
    return this == SIMULATED_SETTLED || this == FAILED;
  }
}
