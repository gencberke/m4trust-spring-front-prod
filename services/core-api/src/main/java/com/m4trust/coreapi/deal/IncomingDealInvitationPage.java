package com.m4trust.coreapi.deal;

import java.util.List;

record IncomingDealInvitationPage(List<IncomingDealInvitation> items, int page,
        int size, long totalElements, int totalPages) {
    IncomingDealInvitationPage { items = List.copyOf(items); }
}
