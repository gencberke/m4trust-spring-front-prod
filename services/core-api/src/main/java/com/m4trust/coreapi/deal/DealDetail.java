package com.m4trust.coreapi.deal;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

record DealDetail(
        UUID id,
        String reference,
        String title,
        String description,
        DealStatus status,
        DealLifecycleProjection lifecycle,
        long version,
        Instant createdAt,
        Instant updatedAt,
        DealAvailableActions availableActions,
        DealParty buyer,
        DealParty seller,
        List<DealParticipant> participants,
        DealCurrentDocumentQueryPort.CurrentDealDocument currentDocument,
        DealAnalysisProjectionPort.AnalysisSummary analysis,
        DealRuleSetProjectionPort.CurrentRuleSet currentRuleSet,
        DealRatificationProjection ratification,
        DealFundingSummary funding,
        DealFulfillmentSummary fulfillment,
        DealCaseworkSummary casework,
        DealSettlementSummary settlement) {

    DealDetail {
        participants = List.copyOf(participants);
    }
}
