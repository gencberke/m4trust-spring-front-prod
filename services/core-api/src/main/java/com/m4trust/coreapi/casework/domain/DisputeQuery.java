package com.m4trust.coreapi.casework.domain;

/** Validated dispute list paging. Range and sort-string checks belong to the API parser. */
public record DisputeQuery(int page, int size, DisputeSort sort) {

  public enum DisputeSort {
    OPENED_AT_ASC,
    OPENED_AT_DESC
  }

  public long offset() {
    return Math.multiplyExact((long) page, size);
  }
}
