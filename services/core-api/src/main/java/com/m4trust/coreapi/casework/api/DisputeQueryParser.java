package com.m4trust.coreapi.casework.api;

import com.m4trust.coreapi.api.api.FieldErrorCode;
import com.m4trust.coreapi.casework.domain.CaseworkExceptions;
import com.m4trust.coreapi.casework.domain.DisputeCommentQuery;
import com.m4trust.coreapi.casework.domain.DisputeQuery;

/** Parses raw query-string values into validated casework domain query records. */
final class DisputeQueryParser {

  private DisputeQueryParser() {}

  static DisputeQuery parseDisputeQuery(String pageValue, String sizeValue, String sortValue) {
    int page = parseInteger(pageValue);
    int size = parseInteger(sizeValue);
    if (page < 0) {
      throw validation("page", FieldErrorCode.OUT_OF_RANGE, "Page must not be negative.");
    }
    if (size < 1 || size > 100) {
      throw validation("size", FieldErrorCode.OUT_OF_RANGE, "Size must be between 1 and 100.");
    }
    DisputeQuery.DisputeSort sort =
        switch (sortValue) {
          case "openedAt,asc" -> DisputeQuery.DisputeSort.OPENED_AT_ASC;
          case "openedAt,desc" -> DisputeQuery.DisputeSort.OPENED_AT_DESC;
          default ->
              throw validation("sort", FieldErrorCode.INVALID_ENUM, "Sort is not supported.");
        };
    return new DisputeQuery(page, size, sort);
  }

  static DisputeCommentQuery parseCommentQuery(
      String pageValue, String sizeValue, String sortValue) {
    int page = parseInteger(pageValue);
    int size = parseInteger(sizeValue);
    if (page < 0) {
      throw validation("page", FieldErrorCode.OUT_OF_RANGE, "Page must not be negative.");
    }
    if (size < 1 || size > 100) {
      throw validation("size", FieldErrorCode.OUT_OF_RANGE, "Size must be between 1 and 100.");
    }
    DisputeCommentQuery.CommentSort sort =
        switch (sortValue) {
          case "createdAt,asc" -> DisputeCommentQuery.CommentSort.CREATED_AT_ASC;
          case "createdAt,desc" -> DisputeCommentQuery.CommentSort.CREATED_AT_DESC;
          default ->
              throw validation("sort", FieldErrorCode.INVALID_ENUM, "Sort is not supported.");
        };
    return new DisputeCommentQuery(page, size, sort);
  }

  private static int parseInteger(String value) {
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException exception) {
      throw new CaseworkExceptions.MalformedRequest();
    }
  }

  private static CaseworkApiExceptions.Validation validation(
      String field, FieldErrorCode code, String message) {
    return new CaseworkApiExceptions.Validation(field, code, message);
  }
}
