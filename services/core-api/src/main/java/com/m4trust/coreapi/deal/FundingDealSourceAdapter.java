package com.m4trust.coreapi.deal;

import java.util.Optional;
import java.util.UUID;

import com.m4trust.coreapi.organization.OperationContext;
import com.m4trust.coreapi.payment.FundingSourcePorts;
import com.m4trust.coreapi.ratification.RatificationPackageProjectionPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deal-owned funding source boundary; payment never reads DealRepository
 * (ADR-003 §23). Mirrors {@code RatificationDealSourceAdapter}. Ratified
 * amount/currency are read through {@link RatificationPackageProjectionPort},
 * the same narrow port {@code DealService} already uses, so payment reaches
 * ratification data only indirectly through Deal and never depends on the
 * ratification module directly.
 */
@Service
class FundingDealSourceAdapter implements FundingSourcePorts.DealTarget {

    private final DealRepository deals;
    private final RatificationPackageProjectionPort ratificationProjections;

    FundingDealSourceAdapter(DealRepository deals, RatificationPackageProjectionPort ratificationProjections) {
        this.deals = deals;
        this.ratificationProjections = ratificationProjections;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<FundingSourcePorts.Target> findVisible(OperationContext context, UUID dealId) {
        return deals.findVisibleById(context.tenantId(), context.activeLegalEntityId(), dealId)
                .map(Deal::rehydrate)
                .map(deal -> target(context, deal));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<FundingSourcePorts.Target> lockVisibleForCreate(OperationContext context, UUID dealId) {
        return deals.findVisibleByIdForUpdate(context.tenantId(), context.activeLegalEntityId(), dealId)
                .map(Deal::rehydrate)
                .map(deal -> target(context, deal));
    }

    private FundingSourcePorts.Target target(OperationContext context, Deal deal) {
        UUID ratifiedPackageId = null;
        Long ratifiedAmountMinor = null;
        String ratifiedCurrency = null;
        UUID currentPackageId = deal.currentRatificationPackageId();
        if (currentPackageId != null) {
            RatificationPackageProjectionPort.CurrentPackage current = ratificationProjections
                    .findCurrentPackage(context, deal.id(), deal.status().name(), currentPackageId)
                    .orElse(null);
            if (current != null && "RATIFIED".equals(current.status())) {
                ratifiedPackageId = currentPackageId;
                ratifiedAmountMinor = current.snapshot().commercialTerms().amountMinor();
                ratifiedCurrency = current.snapshot().commercialTerms().currency();
            }
        }
        return new FundingSourcePorts.Target(deal.id(), deal.toRecord().tenantId(), deal.status().name(),
                deal.version(), deal.buyerLegalEntityId(), deal.sellerLegalEntityId(),
                ratifiedPackageId, ratifiedAmountMinor, ratifiedCurrency);
    }
}
