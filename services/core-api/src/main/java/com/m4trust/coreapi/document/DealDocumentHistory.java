package com.m4trust.coreapi.document;

import java.util.List;

record DealDocumentHistory(List<DealDocumentHistoryItem> items) {

    DealDocumentHistory {
        items = List.copyOf(items);
    }
}
