package com.m4trust.coreapi.payment;

/** Wire shape of {@code ReconcilePaymentOperationRequest}: carries only the PaymentOperation expectedVersion. */
record ReconcilePaymentOperationHttpRequest(long expectedVersion) { }
