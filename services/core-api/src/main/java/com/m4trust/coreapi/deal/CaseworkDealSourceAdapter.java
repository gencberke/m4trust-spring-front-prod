package com.m4trust.coreapi.deal;

import java.util.Optional;
import java.util.UUID;

import com.m4trust.coreapi.casework.CaseworkSourcePorts;
import com.m4trust.coreapi.organization.OperationContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deal-owned casework source boundary; casework never reads DealRepository
 * (ADR-003 §23). Mirrors {@code FulfillmentDealSourceAdapter}.
 */
@Service
class CaseworkDealSourceAdapter implements CaseworkSourcePorts.DealTarget {

    private final DealRepository deals;

    CaseworkDealSourceAdapter(DealRepository deals) {
        this.deals = deals;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CaseworkSourcePorts.DealTargetSnapshot> findVisible(OperationContext context, UUID dealId) {
        return deals.findVisibleById(context.tenantId(), context.activeLegalEntityId(), dealId)
                .map(this::snapshot);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<CaseworkSourcePorts.DealTargetSnapshot> lockVisibleForOpen(
            OperationContext context,
            UUID dealId) {
        return deals.findVisibleByIdForUpdate(context.tenantId(), context.activeLegalEntityId(), dealId)
                .map(this::snapshot);
    }

    private CaseworkSourcePorts.DealTargetSnapshot snapshot(DealRepository.DealRecord record) {
        return new CaseworkSourcePorts.DealTargetSnapshot(
                record.id(),
                record.tenantId(),
                record.status().name(),
                record.version(),
                record.buyerLegalEntityId(),
                record.sellerLegalEntityId());
    }
}
