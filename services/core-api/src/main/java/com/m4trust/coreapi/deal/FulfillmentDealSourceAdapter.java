package com.m4trust.coreapi.deal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.m4trust.coreapi.fulfillment.EvidencePolicy;
import com.m4trust.coreapi.fulfillment.FulfillmentSourcePorts;
import com.m4trust.coreapi.fulfillment.MilestoneRuleReference;
import com.m4trust.coreapi.organization.OperationContext;
import com.m4trust.coreapi.payment.FundingProjectionPort;
import com.m4trust.coreapi.ratification.RatificationPackageProjectionPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deal-owned fulfillment source boundary; fulfillment never reads DealRepository
 * (ADR-003 §23). Mirrors {@code FundingDealSourceAdapter}.
 */
@Service
class FulfillmentDealSourceAdapter implements FulfillmentSourcePorts.DealTarget {

    private final DealRepository deals;
    private final FundingProjectionPort fundingProjections;
    private final RatificationPackageProjectionPort ratificationProjections;

    FulfillmentDealSourceAdapter(DealRepository deals, FundingProjectionPort fundingProjections,
            RatificationPackageProjectionPort ratificationProjections) {
        this.deals = deals;
        this.fundingProjections = fundingProjections;
        this.ratificationProjections = ratificationProjections;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<FulfillmentSourcePorts.Target> findVisible(OperationContext context, UUID dealId) {
        return deals.findVisibleById(context.tenantId(), context.activeLegalEntityId(), dealId)
                .map(Deal::rehydrate)
                .map(deal -> target(context, deal));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<FulfillmentSourcePorts.Target> lockVisibleForStart(OperationContext context, UUID dealId) {
        return deals.findVisibleByIdForUpdate(context.tenantId(), context.activeLegalEntityId(), dealId)
                .map(Deal::rehydrate)
                .map(deal -> target(context, deal));
    }

    private FulfillmentSourcePorts.Target target(OperationContext context, Deal deal) {
        FundingProjectionPort.Summary fundingSummary = fundingProjections.summarize(
                deal.id(), deal.status() == DealStatus.ACTIVE, false);
        RatifiedPackageProjection ratified = ratifiedPackage(context, deal);
        return new FulfillmentSourcePorts.Target(deal.id(), deal.toRecord().tenantId(),
                deal.status().name(), deal.version(), deal.buyerLegalEntityId(),
                deal.sellerLegalEntityId(), fundingSummary.fundingStatus(),
                deal.currentRatificationPackageId(), ratified.evidencePolicy(), ratified.ruleReferences());
    }

    private RatifiedPackageProjection ratifiedPackage(OperationContext context, Deal deal) {
        UUID currentPackageId = deal.currentRatificationPackageId();
        if (currentPackageId == null) {
            return RatifiedPackageProjection.empty();
        }
        return ratificationProjections.findCurrentPackage(context, deal.id(), deal.status().name(), currentPackageId)
                .filter(current -> "RATIFIED".equals(current.status()))
                .map(current -> {
                    EvidencePolicy policy = EvidencePolicy.effectiveFromSnapshot(
                            current.snapshot().schemaVersion(), current.snapshot().evidencePolicy());
                    List<MilestoneRuleReference> refs = current.snapshot().ruleSet().rules().stream()
                            .filter(rule -> "DELIVERY".equals(rule.category()) || "QUALITY".equals(rule.category()))
                            .map(rule -> new MilestoneRuleReference(rule.ruleReference(), rule.category()))
                            .toList();
                    return new RatifiedPackageProjection(policy, refs);
                })
                .orElse(RatifiedPackageProjection.empty());
    }

    private record RatifiedPackageProjection(EvidencePolicy evidencePolicy,
            List<MilestoneRuleReference> ruleReferences) {
        static RatifiedPackageProjection empty() {
            return new RatifiedPackageProjection(null, List.of());
        }
    }
}
