package com.m4trust.coreapi.deal;

import java.util.Optional;
import java.util.UUID;

import com.m4trust.coreapi.organization.OperationContext;

/** Deal-owned locking boundary for the atomic analysis request mutation. */
public interface DealAnalysisMutationPort {

    Optional<AnalysisTarget> lockForAnalysisRequest(OperationContext context, UUID dealId);

    record AnalysisTarget(UUID dealId, UUID owningTenantId, UUID currentDocumentId,
            boolean initiator, boolean acceptsAnalysis) {
    }
}
