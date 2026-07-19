package com.m4trust.coreapi.deal;

import java.util.Optional;
import java.util.UUID;

import com.m4trust.coreapi.organization.OperationContext;

/** Deal-owned participant visibility boundary for analysis projections. */
public interface DealAnalysisReadPort {

    Optional<AnalysisVisibility> findAnalysisVisibility(OperationContext context, UUID dealId);

    record AnalysisVisibility(UUID dealId, UUID owningTenantId,
            UUID currentDocumentId, boolean initiator, boolean acceptsAnalysis) {
    }
}
