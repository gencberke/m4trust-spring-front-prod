package com.m4trust.coreapi.casework;

import java.time.Instant;
import java.util.UUID;

import com.m4trust.coreapi.organization.LegalEntityRole;

/**
 * Casework-owned actor-aware Deal projection consumed by {@code deal.DealService}.
 * Non-party actors receive a hidden projection with no active-case signal.
 */
public interface CaseworkDealProjectionPort {

    ActorSummary forActor(ActorContext context);

    record ActorContext(
            UUID dealId,
            boolean dealActive,
            UUID buyerLegalEntityId,
            UUID sellerLegalEntityId,
            UUID activeLegalEntityId,
            LegalEntityRole activeLegalEntityRole,
            String fulfillmentStatus) {
    }

    record ActorSummary(ActiveDispute activeDispute, boolean canOpenDispute) {

        public static ActorSummary hidden() {
            return new ActorSummary(null, false);
        }
    }

    record ActiveDispute(
            UUID disputeId,
            String status,
            String reasonCode,
            String subject,
            UUID openingLegalEntityId,
            String openingLegalName,
            Instant openedAt,
            Instant acknowledgedAt,
            long version) {
    }
}
