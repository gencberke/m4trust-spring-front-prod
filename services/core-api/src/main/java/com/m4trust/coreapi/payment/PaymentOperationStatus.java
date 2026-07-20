package com.m4trust.coreapi.payment;

/** Closed PaymentOperation state set (ADR-010 §2.3). */
enum PaymentOperationStatus {
    CREATED,
    SUCCEEDED,
    DECLINED,
    UNCONFIRMED
}
