package com.m4trust.coreapi.payment;

enum SettlementStatus {
    NOT_READY,
    READY,
    PROCESSING,
    ON_HOLD,
    SIMULATED_SETTLED,
    FAILED;

    boolean terminal() {
        return this == SIMULATED_SETTLED || this == FAILED;
    }
}
