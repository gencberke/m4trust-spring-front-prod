package com.m4trust.coreapi.payment;

enum ReleaseOperationStatus {
    QUEUED,
    PROCESSING,
    RECONCILIATION_REQUIRED,
    SIMULATED_SETTLED,
    SIMULATED_DECLINED,
    FAILED_BEFORE_DISPATCH;

    boolean terminal() {
        return this == SIMULATED_SETTLED || this == SIMULATED_DECLINED || this == FAILED_BEFORE_DISPATCH;
    }
}
