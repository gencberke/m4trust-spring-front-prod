package com.m4trust.coreapi.casework.api.dto;

import com.m4trust.coreapi.casework.domain.*;
import java.util.List;

public record DisputePage(
    List<DisputeSummary> items, int page, int size, long totalElements, int totalPages) {

  public DisputePage {
    items = List.copyOf(items);
  }
}
