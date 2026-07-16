package com.m4trust.coreapi.organization;

import java.util.List;

public record LegalEntityMembershipList(List<LegalEntityMembership> items) {

    public LegalEntityMembershipList {
        items = List.copyOf(items);
    }
}
