package com.m4trust.coreapi.payment;

/** Closed FundingUnit state set (ADR-010 §2.3). */
enum FundingUnitStatus {
    PLANNED,
    PENDING,
    FUNDED,
    FAILED
}
