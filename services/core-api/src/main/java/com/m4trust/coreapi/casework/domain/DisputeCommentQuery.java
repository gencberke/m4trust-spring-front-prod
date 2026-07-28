package com.m4trust.coreapi.casework.domain;

/** Validated dispute-comment list paging. Range and sort-string checks belong to the API parser. */
public record DisputeCommentQuery(int page, int size, CommentSort sort) {

  public enum CommentSort {
    CREATED_AT_ASC,
    CREATED_AT_DESC
  }

  public long offset() {
    return Math.multiplyExact((long) page, size);
  }
}
