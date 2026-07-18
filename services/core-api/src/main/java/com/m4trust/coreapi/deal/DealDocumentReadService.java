package com.m4trust.coreapi.deal;

import java.util.Optional;
import java.util.UUID;

import com.m4trust.coreapi.organization.OperationContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Keeps Deal participant/initiator visibility checks for document reads Deal-owned. */
@Service
class DealDocumentReadService implements DealDocumentReadPort {

    private final DealRepository repository;
    private final DealOperationPolicy operationPolicy;

    DealDocumentReadService(DealRepository repository,
            DealOperationPolicy operationPolicy) {
        this.repository = repository;
        this.operationPolicy = operationPolicy;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<DealDocumentVisibility> findVisibility(
            OperationContext context, UUID dealId) {
        return repository.findVisibleById(context.tenantId(),
                        context.activeLegalEntityId(), dealId)
                .map(Deal::rehydrate)
                .map(deal -> new DealDocumentVisibility(deal.id(), context.tenantId(),
                        deal.currentDocumentId(), operationPolicy.isInitiator(deal, context)));
    }
}
