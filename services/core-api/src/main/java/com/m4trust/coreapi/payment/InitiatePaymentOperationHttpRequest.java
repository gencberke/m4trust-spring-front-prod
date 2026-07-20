package com.m4trust.coreapi.payment;

/** Wire shape of {@code InitiatePaymentOperationRequest}: carries only the FundingUnit expectedVersion. */
record InitiatePaymentOperationHttpRequest(long expectedVersion) { }
