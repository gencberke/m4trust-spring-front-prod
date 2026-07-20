package com.m4trust.coreapi.payment;

import java.util.UUID;

import com.m4trust.coreapi.organization.OperationContext;
import com.m4trust.coreapi.organization.RequestedOperation;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Participant-readable funding plan / payment operation projections. */
@Service
class FundingReadService {

    private final FundingSourcePorts.DealTarget deals;
    private final FundingRepository funding;

    FundingReadService(FundingSourcePorts.DealTarget deals, FundingRepository funding) {
        this.deals = deals;
        this.funding = funding;
    }

    @Transactional(readOnly = true)
    FundingReadDtos.FundingPlanDetailView getPlan(OperationContext context, UUID dealId) {
        requireOperation(context, RequestedOperation.DEAL_FUNDING_PLAN_READ);
        FundingSourcePorts.Target target = deals.findVisible(context, dealId)
                .orElseThrow(PaymentExceptions.FundingPlanNotFound::new);
        FundingRepository.PlanRecord plan = funding.findPlanByDeal(dealId)
                .orElseThrow(PaymentExceptions.FundingPlanNotFound::new);
        FundingRepository.UnitRecord unit = funding.findUnitByPlan(plan.id())
                .orElseThrow(() -> new IllegalStateException("Funding plan is missing its funding unit"));
        FundingRepository.OperationRecord currentOperation = funding.findCurrentOperation(unit.id()).orElse(null);
        boolean buyerAdmin = FundingProjection.isBuyerAdmin(context, target);
        return FundingProjection.plan(plan, unit, currentOperation, buyerAdmin, "ACTIVE".equals(target.status()));
    }

    @Transactional(readOnly = true)
    FundingReadDtos.PaymentOperationView getOperation(OperationContext context, UUID operationId) {
        requireOperation(context, RequestedOperation.PAYMENT_OPERATION_READ);
        FundingRepository.OperationLookup lookup = funding.findOperationById(operationId)
                .orElseThrow(PaymentExceptions.PaymentOperationNotFound::new);
        FundingSourcePorts.Target target = deals.findVisible(context, lookup.dealId())
                .orElseThrow(PaymentExceptions.PaymentOperationNotFound::new);
        boolean buyerAdmin = FundingProjection.isBuyerAdmin(context, target);
        return FundingProjection.operation(lookup.operation(), buyerAdmin);
    }

    private static void requireOperation(OperationContext context, RequestedOperation expected) {
        if (context.requestedOperation() != expected) {
            throw new IllegalArgumentException("Operation context does not match the requested use case");
        }
    }
}
