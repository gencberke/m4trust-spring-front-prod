package com.m4trust.coreapi.deal;

import com.m4trust.coreapi.api.FieldErrorCode;
import com.m4trust.coreapi.deal.DealRepository.DealSort;

record DealQuery(
        int page,
        int size,
        DealStatus status,
        DealSort sort) {

    static DealQuery parse(String pageValue, String sizeValue,
            String statusValue, String sortValue) {
        int page = parseInteger(pageValue);
        int size = parseInteger(sizeValue);
        if (page < 0) {
            throw validation("page", FieldErrorCode.OUT_OF_RANGE,
                    "Page must not be negative.");
        }
        if (size < 1 || size > 100) {
            throw validation("size", FieldErrorCode.OUT_OF_RANGE,
                    "Size must be between 1 and 100.");
        }
        DealStatus status = null;
        if (statusValue != null) {
            try {
                status = DealStatus.valueOf(statusValue);
            } catch (IllegalArgumentException exception) {
                throw validation("status", FieldErrorCode.INVALID_ENUM,
                        "Status is not supported.");
            }
        }
        DealSort sort = switch (sortValue) {
            case "createdAt,asc" -> DealSort.CREATED_AT_ASC;
            case "createdAt,desc" -> DealSort.CREATED_AT_DESC;
            case "title,asc" -> DealSort.TITLE_ASC;
            case "title,desc" -> DealSort.TITLE_DESC;
            default -> throw validation("sort", FieldErrorCode.INVALID_SORT,
                    "Sort is not supported.");
        };
        return new DealQuery(page, size, status, sort);
    }

    long offset() {
        return Math.multiplyExact((long) page, size);
    }

    private static int parseInteger(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new MalformedDealRequestException();
        }
    }

    private static DealValidationException validation(
            String field, FieldErrorCode code, String message) {
        return new DealValidationException(field, code, message);
    }
}
