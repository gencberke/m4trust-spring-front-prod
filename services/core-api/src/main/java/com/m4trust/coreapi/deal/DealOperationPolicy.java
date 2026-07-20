package com.m4trust.coreapi.deal;

import com.m4trust.coreapi.organization.OperationContext;
import org.springframework.stereotype.Component;

/**
 * Central Deal operation policy. The immutable initiator legal entity is the
 * sole source of DRAFT coordination authority; participant visibility does not
 * grant mutation authority.
 */
@Component
class DealOperationPolicy {

    boolean isInitiator(Deal deal, OperationContext context) {
        return deal.isInitiatedBy(context.activeLegalEntityId());
    }

    void requireInitiator(Deal deal, OperationContext context) {
        if (!isInitiator(deal, context)) {
            throw new DealMutationForbiddenException();
        }
    }

    DealAvailableActions availableActions(Deal deal,
            OperationContext context) {
        boolean isInitiator = isInitiator(deal, context);
        return new DealAvailableActions(
                isInitiator && deal.status().allowsBasicFieldEditing(),
                isInitiator && deal.status().allowsCancellation(),
                isInitiator && deal.status() == DealStatus.DRAFT,
                isInitiator && deal.status() == DealStatus.DRAFT,
                isInitiator && deal.status().allowsDocumentUpload(), false, false,
                false, false, false, false, false, false);
    }
}
