package com.m4trust.coreapi.payment.domain;

/** Closed FundingUnit state set (ADR-010 §2.3). */
public enum FundingUnitStatus {
  PLANNED,
  PENDING,
  FUNDED,
  FAILED
}
