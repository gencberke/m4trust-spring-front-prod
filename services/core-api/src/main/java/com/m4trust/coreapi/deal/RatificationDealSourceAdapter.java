package com.m4trust.coreapi.deal;

import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.m4trust.coreapi.organization.InvitationLegalEntityQueryPort;
import com.m4trust.coreapi.organization.OperationContext;
import com.m4trust.coreapi.ratification.RatificationSourcePorts;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Deal-owned ratification source boundary; ratification never reads DealRepository. */
@Service
class RatificationDealSourceAdapter implements RatificationSourcePorts.DealTarget {

    private final DealRepository deals;
    private final DealOperationPolicy operationPolicy;
    private final InvitationLegalEntityQueryPort legalEntities;

    RatificationDealSourceAdapter(
            DealRepository deals,
            DealOperationPolicy operationPolicy,
            InvitationLegalEntityQueryPort legalEntities) {
        this.deals = deals;
        this.operationPolicy = operationPolicy;
        this.legalEntities = legalEntities;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RatificationSourcePorts.Target> findVisible(
            OperationContext context, UUID dealId) {
        return deals.findVisibleById(context.tenantId(), context.activeLegalEntityId(), dealId)
                .map(Deal::rehydrate)
                .map(deal -> target(context, deal));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<RatificationSourcePorts.Target> lockVisibleForCreate(
            OperationContext context, UUID dealId) {
        return deals.findVisibleByIdForUpdate(context.tenantId(), context.activeLegalEntityId(), dealId)
                .map(Deal::rehydrate)
                .map(deal -> target(context, deal));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void pointCurrentPackage(UUID dealId, UUID packageId, Instant changedAt) {
        deals.pointCurrentRatificationPackage(dealId, packageId, changedAt);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void activateCurrentPackage(UUID dealId, UUID packageId, Instant changedAt) {
        Deal deal = deals.findByIdForUpdate(dealId).map(Deal::rehydrate)
                .orElseThrow(IllegalStateException::new);
        long version = deal.version();
        deal.activateCurrentRatificationPackage(packageId, changedAt);
        if (!deals.activateCurrentRatificationPackage(
                dealId, packageId, version, changedAt)) {
            throw new IllegalStateException("Deal activation was stale");
        }
    }

    private RatificationSourcePorts.Target target(OperationContext context, Deal deal) {
        Map<UUID, String> names = legalEntities.findLegalNames(assignedParties(deal));
        return new RatificationSourcePorts.Target(
                deal.id(),
                deal.toRecord().tenantId(),
                deal.status().name(),
                deal.version(),
                deal.reference(),
                deal.title(),
                operationPolicy.isInitiator(deal, context),
                party(deal.buyerLegalEntityId(), names),
                party(deal.sellerLegalEntityId(), names),
                deal.currentDocumentId(),
                deal.currentRuleSetVersionId(),
                deal.currentRatificationPackageId());
    }

    private static Set<UUID> assignedParties(Deal deal) {
        Set<UUID> ids = new HashSet<>();
        if (deal.buyerLegalEntityId() != null) {
            ids.add(deal.buyerLegalEntityId());
        }
        if (deal.sellerLegalEntityId() != null) {
            ids.add(deal.sellerLegalEntityId());
        }
        return Set.copyOf(ids);
    }

    private static RatificationSourcePorts.Party party(UUID legalEntityId, Map<UUID, String> names) {
        if (legalEntityId == null) {
            return null;
        }
        String legalName = names.get(legalEntityId);
        if (legalName == null) {
            throw new IllegalStateException("Assigned Deal party has no legal-name projection");
        }
        return new RatificationSourcePorts.Party(legalEntityId, legalName);
    }
}
