package com.m4trust.coreapi.payment;

import java.util.UUID;

/**
 * Payment-owned projection port consumed by {@code deal.DealService} the same
 * way {@code RatificationPackageProjectionPort} is consumed: the Deal module
 * reaches funding data only through this narrow port and payment never reads
 * DealRepository (ADR-003 §23).
 */
public interface FundingProjectionPort {

    /**
     * @param dealActive whether the Deal is currently ACTIVE (funding mutation
     *     authority requires it).
     * @param callerIsBuyerAdmin whether the active legal entity is the Deal's
     *     buyer and the caller holds ADMIN on it (ADR-010 §2.2).
     */
    Summary summarize(UUID dealId, boolean dealActive, boolean callerIsBuyerAdmin);

    record Summary(
            String fundingStatus,
            UUID fundingPlanId,
            Long amountMinor,
            String currency,
            boolean canCreateFundingPlan,
            boolean canInitiateFunding,
            boolean canReconcilePaymentOperation) {
    }
}
