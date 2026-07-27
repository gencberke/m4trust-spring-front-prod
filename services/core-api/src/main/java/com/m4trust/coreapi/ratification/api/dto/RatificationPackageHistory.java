package com.m4trust.coreapi.ratification.api.dto;

import java.util.List;

public record RatificationPackageHistory(List<RatificationPackageDetail> items) {
  public RatificationPackageHistory {
    items = List.copyOf(items);
  }
}
