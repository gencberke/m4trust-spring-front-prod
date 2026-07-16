package com.m4trust.coreapi.organization;

import java.util.List;

public record LegalEntityMemberList(List<LegalEntityMember> items) {

    public LegalEntityMemberList {
        items = List.copyOf(items);
    }
}
