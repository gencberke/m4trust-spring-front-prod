package com.m4trust.coreapi.payment.domain;

/** Closed PaymentOperation state set. */
public enum PaymentOperationStatus {
  CREATED,
  SUCCEEDED,
  DECLINED,
  UNCONFIRMED
}
