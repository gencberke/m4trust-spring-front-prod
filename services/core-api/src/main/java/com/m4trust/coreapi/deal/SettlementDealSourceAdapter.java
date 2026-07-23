package com.m4trust.coreapi.deal;

import java.util.Optional;
import java.util.UUID;

import com.m4trust.coreapi.organization.OperationContext;
import com.m4trust.coreapi.payment.SettlementSourcePorts;
import com.m4trust.coreapi.ratification.RatificationPackageProjectionPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
class SettlementDealSourceAdapter implements SettlementSourcePorts.DealTarget {

    private final DealRepository deals;
    private final RatificationPackageProjectionPort ratificationProjections;

    SettlementDealSourceAdapter(DealRepository deals, RatificationPackageProjectionPort ratificationProjections) {
        this.deals = deals;
        this.ratificationProjections = ratificationProjections;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SettlementSourcePorts.DealSnapshot> findVisible(OperationContext context, UUID dealId) {
        return deals.findVisibleById(context.tenantId(), context.activeLegalEntityId(), dealId)
                .map(Deal::rehydrate)
                .map(deal -> snapshot(context, deal));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<SettlementSourcePorts.DealSnapshot> lockVisible(OperationContext context, UUID dealId) {
        return deals.findVisibleByIdForUpdate(context.tenantId(), context.activeLegalEntityId(), dealId)
                .map(Deal::rehydrate)
                .map(deal -> snapshot(context, deal));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SettlementSourcePorts.DealSnapshot> findForProjection(UUID dealId) {
        return deals.findById(dealId).map(Deal::rehydrate).map(this::snapshotForProjection);
    }

    private SettlementSourcePorts.DealSnapshot snapshotForProjection(Deal deal) {
        return new SettlementSourcePorts.DealSnapshot(deal.id(), deal.toRecord().tenantId(), deal.status().name(),
                deal.version(), deal.buyerLegalEntityId(), deal.sellerLegalEntityId(),
                deal.currentRatificationPackageId());
    }

    private SettlementSourcePorts.DealSnapshot snapshot(OperationContext context, Deal deal) {
        UUID ratifiedPackageId = null;
        UUID currentPackageId = deal.currentRatificationPackageId();
        if (currentPackageId != null) {
            String status = ratificationProjections.findPackageStatus(deal.id(), currentPackageId).orElse(null);
            if ("RATIFIED".equals(status)) {
                ratifiedPackageId = currentPackageId;
            }
        }
        return new SettlementSourcePorts.DealSnapshot(deal.id(), deal.toRecord().tenantId(), deal.status().name(),
                deal.version(), deal.buyerLegalEntityId(), deal.sellerLegalEntityId(), ratifiedPackageId);
    }
}
