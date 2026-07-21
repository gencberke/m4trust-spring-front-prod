package com.m4trust.coreapi.casework;

import java.util.Set;
import java.util.UUID;

import com.m4trust.coreapi.organization.LegalEntityRole;
import org.springframework.stereotype.Service;

/** Casework-owned actor-aware Deal projection for lifecycle and open availability. */
@Service
class CaseworkDealProjectionAdapter implements CaseworkDealProjectionPort {

    private static final Set<String> ELIGIBLE_FULFILLMENT_STATUSES = Set.of(
            "IN_PROGRESS", "EVIDENCE_REQUIRED", "REVIEW_REQUIRED", "COMPLETED");

    private final DisputeCaseRepository disputeCases;

    CaseworkDealProjectionAdapter(DisputeCaseRepository disputeCases) {
        this.disputeCases = disputeCases;
    }

    @Override
    public ActorSummary forActor(ActorContext context) {
        PartyRole role = partyRole(context);
        if (role == PartyRole.NONE) {
            return ActorSummary.hidden();
        }
        var active = disputeCases.findActiveByDealId(context.dealId());
        ActiveDispute dispute = active.map(this::toActiveDispute).orElse(null);
        boolean canOpen = canOpenDispute(context, active.isPresent());
        return new ActorSummary(dispute, canOpen);
    }

    private ActiveDispute toActiveDispute(DisputeCase.DisputeCaseRecord record) {
        return new ActiveDispute(
                record.id(),
                record.status().name(),
                record.reasonCode().name(),
                record.subject(),
                record.openingLegalEntityId(),
                record.openingLegalName(),
                record.openedAt(),
                record.acknowledgedAt(),
                record.version());
    }

    private boolean canOpenDispute(ActorContext context, boolean hasActiveCase) {
        if (hasActiveCase
                || !context.dealActive()
                || context.activeLegalEntityRole() != LegalEntityRole.ADMIN
                || partyRole(context) == PartyRole.NONE) {
            return false;
        }
        return context.fulfillmentStatus() != null
                && ELIGIBLE_FULFILLMENT_STATUSES.contains(context.fulfillmentStatus());
    }

    private PartyRole partyRole(ActorContext context) {
        UUID actor = context.activeLegalEntityId();
        if (actor != null && actor.equals(context.buyerLegalEntityId())) {
            return PartyRole.BUYER;
        }
        if (actor != null && actor.equals(context.sellerLegalEntityId())) {
            return PartyRole.SELLER;
        }
        return PartyRole.NONE;
    }

    private enum PartyRole {
        BUYER,
        SELLER,
        NONE
    }
}
