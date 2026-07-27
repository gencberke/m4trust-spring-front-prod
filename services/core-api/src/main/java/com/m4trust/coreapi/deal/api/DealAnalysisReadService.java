package com.m4trust.coreapi.deal.api;

import com.m4trust.coreapi.deal.api.port.*;
import com.m4trust.coreapi.deal.domain.*;
import com.m4trust.coreapi.deal.infra.repository.*;
import com.m4trust.coreapi.organization.domain.OperationContext;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class DealAnalysisReadService implements DealAnalysisReadPort {

  private final DealRepository repository;
  private final DealOperationPolicy operationPolicy;

  DealAnalysisReadService(DealRepository repository, DealOperationPolicy operationPolicy) {
    this.repository = repository;
    this.operationPolicy = operationPolicy;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<AnalysisVisibility> findAnalysisVisibility(
      OperationContext context, UUID dealId) {
    return repository
        .findVisibleById(context.tenantId(), context.activeLegalEntityId(), dealId)
        .map(Deal::rehydrate)
        .map(
            deal ->
                new AnalysisVisibility(
                    deal.id(),
                    deal.toRecord().tenantId(),
                    deal.currentDocumentId(),
                    operationPolicy.isInitiator(deal, context),
                    deal.status().allowsDocumentUpload()));
  }
}
