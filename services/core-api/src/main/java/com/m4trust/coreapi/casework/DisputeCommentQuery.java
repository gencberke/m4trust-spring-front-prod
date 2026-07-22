package com.m4trust.coreapi.casework;

import com.m4trust.coreapi.api.FieldErrorCode;

record DisputeCommentQuery(int page, int size, CommentSort sort) {

    enum CommentSort {
        CREATED_AT_ASC,
        CREATED_AT_DESC
    }

    static DisputeCommentQuery parse(String pageValue, String sizeValue, String sortValue) {
        int page = parseInteger(pageValue);
        int size = parseInteger(sizeValue);
        if (page < 0) {
            throw validation("page", FieldErrorCode.OUT_OF_RANGE, "Page must not be negative.");
        }
        if (size < 1 || size > 100) {
            throw validation("size", FieldErrorCode.OUT_OF_RANGE, "Size must be between 1 and 100.");
        }
        CommentSort sort = switch (sortValue) {
            case "createdAt,asc" -> CommentSort.CREATED_AT_ASC;
            case "createdAt,desc" -> CommentSort.CREATED_AT_DESC;
            default -> throw validation("sort", FieldErrorCode.INVALID_ENUM, "Sort is not supported.");
        };
        return new DisputeCommentQuery(page, size, sort);
    }

    long offset() {
        return Math.multiplyExact((long) page, size);
    }

    private static int parseInteger(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new CaseworkExceptions.MalformedRequest();
        }
    }

    private static CaseworkExceptions.Validation validation(String field, FieldErrorCode code, String message) {
        return new CaseworkExceptions.Validation(field, code, message);
    }
}
