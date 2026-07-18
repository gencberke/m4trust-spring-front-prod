package com.m4trust.coreapi.deal;

import java.util.List;

record DealInvitationPage(List<DealInvitationProjection> items, int page,
        int size, long totalElements, int totalPages) {
    DealInvitationPage { items = List.copyOf(items); }
}
