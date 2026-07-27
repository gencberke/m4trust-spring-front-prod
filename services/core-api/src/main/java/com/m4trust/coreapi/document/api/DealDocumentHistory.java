package com.m4trust.coreapi.document.api;

import com.m4trust.coreapi.document.domain.*;
import com.m4trust.coreapi.document.infra.*;
import java.util.List;

record DealDocumentHistory(List<DealDocumentHistoryItem> items) {

  DealDocumentHistory {
    items = List.copyOf(items);
  }
}
