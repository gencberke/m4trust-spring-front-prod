package com.m4trust.coreapi.deal.api;

import com.m4trust.coreapi.deal.api.port.*;
import com.m4trust.coreapi.deal.domain.*;
import com.m4trust.coreapi.deal.infra.repository.*;
import com.m4trust.coreapi.organization.domain.OperationContext;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
class DealAnalysisMutationService implements DealAnalysisMutationPort {

  private final DealRepository repository;
  private final DealOperationPolicy operationPolicy;

  DealAnalysisMutationService(DealRepository repository, DealOperationPolicy operationPolicy) {
    this.repository = repository;
    this.operationPolicy = operationPolicy;
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public Optional<AnalysisTarget> lockForAnalysisRequest(OperationContext context, UUID dealId) {
    return repository
        .findVisibleByIdForUpdate(context.tenantId(), context.activeLegalEntityId(), dealId)
        .map(Deal::rehydrate)
        .map(
            deal ->
                new AnalysisTarget(
                    deal.id(),
                    deal.toRecord().tenantId(),
                    deal.currentDocumentId(),
                    operationPolicy.isInitiator(deal, context),
                    deal.status().allowsDocumentUpload()));
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public Optional<ReviewTarget> lockForReview(OperationContext context, UUID dealId) {
    return repository
        .findVisibleByIdForUpdate(context.tenantId(), context.activeLegalEntityId(), dealId)
        .map(Deal::rehydrate)
        .map(
            deal ->
                new ReviewTarget(
                    deal.id(),
                    deal.toRecord().tenantId(),
                    deal.currentDocumentId(),
                    deal.currentRatificationPackageId(),
                    deal.version(),
                    operationPolicy.isInitiator(deal, context),
                    deal.status() == DealStatus.DRAFT));
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void setCurrentRuleSet(UUID dealId, UUID ruleSetVersionId, java.time.Instant changedAt) {
    repository.setCurrentRuleSet(dealId, ruleSetVersionId, changedAt);
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void clearCurrentRuleSet(UUID dealId) {
    repository.clearCurrentRuleSetForDocumentSupersession(dealId);
  }
}
