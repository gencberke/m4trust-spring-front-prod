package com.m4trust.coreapi.deal;

import java.util.List;

record DealPage(
        List<DealSummary> items,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    DealPage {
        items = List.copyOf(items);
    }
}
