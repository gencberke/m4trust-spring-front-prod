package com.m4trust.coreapi.casework.api.dto;

import com.m4trust.coreapi.casework.domain.*;
import java.util.List;

public record DisputeCommentPage(
    List<DisputeComment> items, int page, int size, long totalElements, int totalPages) {

  public DisputeCommentPage {
    items = List.copyOf(items);
  }
}
