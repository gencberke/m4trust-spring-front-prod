package com.m4trust.coreapi.casework;

import com.m4trust.coreapi.api.FieldErrorCode;

record DisputeQuery(int page, int size, DisputeSort sort) {

    enum DisputeSort {
        OPENED_AT_ASC,
        OPENED_AT_DESC
    }

    static DisputeQuery parse(String pageValue, String sizeValue, String sortValue) {
        int page = parseInteger(pageValue);
        int size = parseInteger(sizeValue);
        if (page < 0) {
            throw validation("page", FieldErrorCode.OUT_OF_RANGE, "Page must not be negative.");
        }
        if (size < 1 || size > 100) {
            throw validation("size", FieldErrorCode.OUT_OF_RANGE, "Size must be between 1 and 100.");
        }
        DisputeSort sort = switch (sortValue) {
            case "openedAt,asc" -> DisputeSort.OPENED_AT_ASC;
            case "openedAt,desc" -> DisputeSort.OPENED_AT_DESC;
            default -> throw validation("sort", FieldErrorCode.INVALID_ENUM, "Sort is not supported.");
        };
        return new DisputeQuery(page, size, sort);
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
