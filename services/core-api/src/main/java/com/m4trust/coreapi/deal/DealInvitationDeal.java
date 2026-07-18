package com.m4trust.coreapi.deal;

import java.util.UUID;

record DealInvitationDeal(UUID id, String reference, String title,
        String initiatorLegalName) {
}
