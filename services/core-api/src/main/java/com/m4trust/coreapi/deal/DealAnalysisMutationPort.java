package com.m4trust.coreapi.deal;

import java.util.Optional;
import java.util.UUID;
import com.m4trust.coreapi.organization.OperationContext;

public interface DealAnalysisMutationPort {
    Optional<AnalysisTarget> lockForAnalysis(OperationContext context, UUID dealId);
    record AnalysisTarget(UUID dealId, UUID tenantId, UUID currentDocumentId,
            boolean initiator, boolean acceptsAnalysis) { }
}
