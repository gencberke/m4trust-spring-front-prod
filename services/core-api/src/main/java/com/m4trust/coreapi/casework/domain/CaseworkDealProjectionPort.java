package com.m4trust.coreapi.casework.domain;

import com.m4trust.coreapi.organization.domain.LegalEntityRole;
import java.time.Instant;
import java.util.UUID;

/**
 * Casework-owned actor-aware Deal projection consumed by {@code deal.DealService}. Non-party actors
 * receive a hidden projection with no active-case signal.
 */
public interface CaseworkDealProjectionPort {

  ActorSummary forActor(ActorContext context);

  public record ActorContext(
      UUID dealId,
      boolean dealActive,
      UUID buyerLegalEntityId,
      UUID sellerLegalEntityId,
      UUID activeLegalEntityId,
      LegalEntityRole activeLegalEntityRole,
      String fulfillmentStatus) {}

  public record ActorSummary(ActiveDispute activeDispute, boolean canOpenDispute) {

    public static ActorSummary hidden() {
      return new ActorSummary(null, false);
    }
  }

  public record ActiveDispute(
      UUID disputeId,
      String status,
      String reasonCode,
      String subject,
      UUID openingLegalEntityId,
      String openingLegalName,
      Instant openedAt,
      Instant acknowledgedAt,
      long version) {}
}
