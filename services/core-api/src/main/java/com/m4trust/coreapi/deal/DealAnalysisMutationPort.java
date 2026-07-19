package com.m4trust.coreapi.deal;

import java.util.Optional;
import java.util.UUID;

import com.m4trust.coreapi.organization.OperationContext;

/** Deal-owned locking boundary for the atomic analysis request mutation. */
public interface DealAnalysisMutationPort {

    Optional<AnalysisTarget> lockForAnalysisRequest(OperationContext context, UUID dealId);

    Optional<ReviewTarget> lockForReview(OperationContext context, UUID dealId);

    void setCurrentRuleSet(UUID dealId, UUID ruleSetVersionId, java.time.Instant changedAt);

    void clearCurrentRuleSet(UUID dealId, java.time.Instant changedAt);

    record AnalysisTarget(UUID dealId, UUID owningTenantId, UUID currentDocumentId,
            boolean initiator, boolean acceptsAnalysis) {
    }
    record ReviewTarget(UUID dealId, UUID owningTenantId, UUID currentDocumentId,
            long version, boolean initiator, boolean reviewEligible) { }
}
