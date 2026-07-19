package com.m4trust.coreapi.deal;

import java.util.Optional;
import java.util.UUID;

import com.m4trust.coreapi.organization.OperationContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
class DealAnalysisMutationService implements DealAnalysisMutationPort {

    private final DealRepository repository;
    private final DealOperationPolicy operationPolicy;

    DealAnalysisMutationService(DealRepository repository,
            DealOperationPolicy operationPolicy) {
        this.repository = repository;
        this.operationPolicy = operationPolicy;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<AnalysisTarget> lockForAnalysisRequest(
            OperationContext context, UUID dealId) {
        return repository.findVisibleByIdForUpdate(context.tenantId(),
                        context.activeLegalEntityId(), dealId)
                .map(Deal::rehydrate)
                .map(deal -> new AnalysisTarget(deal.id(), deal.toRecord().tenantId(),
                        deal.currentDocumentId(), operationPolicy.isInitiator(deal, context),
                        deal.status().allowsDocumentUpload()));
    }

    @Override @Transactional(propagation = Propagation.MANDATORY)
    public Optional<ReviewTarget> lockForReview(OperationContext context, UUID dealId) {
        return repository.findVisibleByIdForUpdate(context.tenantId(), context.activeLegalEntityId(), dealId)
                .map(Deal::rehydrate)
                .map(deal -> new ReviewTarget(deal.id(), deal.toRecord().tenantId(),
                        deal.currentDocumentId(), deal.version(), operationPolicy.isInitiator(deal, context),
                        deal.status() == DealStatus.DRAFT));
    }

    @Override @Transactional(propagation = Propagation.MANDATORY)
    public void setCurrentRuleSet(UUID dealId, UUID ruleSetVersionId, java.time.Instant changedAt) {
        repository.setCurrentRuleSet(dealId, ruleSetVersionId, changedAt);
    }

    @Override @Transactional(propagation = Propagation.MANDATORY)
    public void clearCurrentRuleSet(UUID dealId, java.time.Instant changedAt) {
        repository.setCurrentRuleSet(dealId, null, changedAt);
    }
}
