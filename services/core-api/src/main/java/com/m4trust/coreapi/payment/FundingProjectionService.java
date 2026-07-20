package com.m4trust.coreapi.payment;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Implements the projection port {@code deal.DealService} consumes for its embedded funding summary. */
@Service
class FundingProjectionService implements FundingProjectionPort {

    private final FundingRepository funding;

    FundingProjectionService(FundingRepository funding) {
        this.funding = funding;
    }

    @Override
    @Transactional(readOnly = true)
    public Summary summarize(UUID dealId, boolean dealActive, boolean callerIsBuyerAdmin) {
        var planRecord = funding.findPlanByDeal(dealId);
        if (planRecord.isEmpty()) {
            boolean canCreate = callerIsBuyerAdmin && dealActive;
            return new Summary("NOT_CONFIGURED", null, null, null, canCreate, false, false);
        }
        FundingRepository.PlanRecord plan = planRecord.get();
        FundingRepository.UnitRecord unit = funding.findUnitByPlan(plan.id())
                .orElseThrow(() -> new IllegalStateException("Funding plan is missing its funding unit"));
        FundingRepository.OperationRecord currentOperation = funding.findCurrentOperation(unit.id()).orElse(null);
        boolean inFlight = currentOperation != null
                && (currentOperation.status() == PaymentOperationStatus.CREATED
                        || currentOperation.status() == PaymentOperationStatus.UNCONFIRMED);
        boolean canInitiate = callerIsBuyerAdmin && dealActive
                && unit.status() != FundingUnitStatus.FUNDED && !inFlight;
        boolean canReconcile = callerIsBuyerAdmin && currentOperation != null
                && currentOperation.status() == PaymentOperationStatus.UNCONFIRMED;
        return new Summary(FundingProjection.dealFundingStatus(unit.status()), plan.id(), plan.amountMinor(),
                plan.currency(), false, canInitiate, canReconcile);
    }
}
