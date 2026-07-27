package com.m4trust.coreapi.deal.api.port;

import com.m4trust.coreapi.deal.domain.*;
import com.m4trust.coreapi.organization.domain.OperationContext;
import java.util.Optional;
import java.util.UUID;

/** Deal-owned participant visibility boundary for analysis projections. */
public interface DealAnalysisReadPort {

  Optional<AnalysisVisibility> findAnalysisVisibility(OperationContext context, UUID dealId);

  record AnalysisVisibility(
      UUID dealId,
      UUID owningTenantId,
      UUID currentDocumentId,
      boolean initiator,
      boolean acceptsAnalysis) {}
}
