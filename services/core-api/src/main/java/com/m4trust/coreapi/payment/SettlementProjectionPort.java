package com.m4trust.coreapi.payment;

import java.util.UUID;

/**
 * Settlement-owned projection port consumed by {@code deal.DealService} for the
 * embedded settlement summary and release action flags.
 */
public interface SettlementProjectionPort {

    Summary summarize(UUID dealId, boolean dealActive, boolean callerIsBuyerAdmin);

    record Summary(
            UUID settlementId,
            String status,
            UUID currentReleaseOperationId,
            boolean canRequestRelease,
            boolean canReconcileRelease) {
    }
}
