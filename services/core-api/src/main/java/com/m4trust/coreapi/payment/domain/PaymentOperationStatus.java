package com.m4trust.coreapi.payment.domain;

/** Closed PaymentOperation state set (ADR-010 §2.3). */
public enum PaymentOperationStatus {
  CREATED,
  SUCCEEDED,
  DECLINED,
  UNCONFIRMED
}
