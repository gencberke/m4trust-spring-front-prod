package com.m4trust.coreapi.deal;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.m4trust.coreapi.organization.OperationContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Keeps Deal locking, visibility, and current-document-pointer writes Deal-owned. */
@Service
class DealDocumentMutationService implements DealDocumentMutationPort {

    private final DealRepository repository;
    private final DealOperationPolicy operationPolicy;

    DealDocumentMutationService(DealRepository repository,
            DealOperationPolicy operationPolicy) {
        this.repository = repository;
        this.operationPolicy = operationPolicy;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<LockedDealDocumentTarget> lockForDocumentMutation(
            OperationContext context, UUID dealId) {
        return repository.findVisibleByIdForUpdate(context.tenantId(),
                        context.activeLegalEntityId(), dealId)
                .map(Deal::rehydrate)
                .map(deal -> new LockedDealDocumentTarget(deal.id(), context.tenantId(),
                        deal.currentDocumentId(), deal.currentRatificationPackageId(),
                        operationPolicy.isInitiator(deal, context),
                        deal.status().allowsDocumentUpload()));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void repointCurrentDocument(UUID dealId, UUID documentId,
            Instant changedAt) {
        if (!repository.repointCurrentDocument(dealId, documentId, changedAt)) {
            throw new IllegalStateException("Locked Deal current-document pointer could not be updated");
        }
    }
}
