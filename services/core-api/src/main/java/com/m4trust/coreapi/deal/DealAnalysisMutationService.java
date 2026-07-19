package com.m4trust.coreapi.deal;

import java.util.Optional;
import java.util.UUID;
import com.m4trust.coreapi.organization.OperationContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
class DealAnalysisMutationService implements DealAnalysisMutationPort {
 private final DealRepository repository; private final DealOperationPolicy policy;
 DealAnalysisMutationService(DealRepository repository, DealOperationPolicy policy) { this.repository=repository; this.policy=policy; }
 @Override @Transactional(propagation = Propagation.MANDATORY)
 public Optional<AnalysisTarget> lockForAnalysis(OperationContext context, UUID dealId) {
  return repository.findVisibleByIdForUpdate(context.tenantId(), context.activeLegalEntityId(), dealId).map(Deal::rehydrate)
   .map(d -> new AnalysisTarget(d.id(), context.tenantId(), d.currentDocumentId(), policy.isInitiator(d, context), d.status().allowsDocumentUpload()));
 }
}
