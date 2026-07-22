package com.m4trust.coreapi.deal;

import com.m4trust.coreapi.api.FieldErrorCode;

record InvitationPageQuery(int page, int size) {
    static InvitationPageQuery parse(String pageValue, String sizeValue) {
        try {
            int page = Integer.parseInt(pageValue);
            int size = Integer.parseInt(sizeValue);
            if (page < 0) {
                throw validation("page", FieldErrorCode.OUT_OF_RANGE, "Page must not be negative.");
            }
            if (size < 1 || size > 100) {
                throw validation("size", FieldErrorCode.OUT_OF_RANGE, "Size must be between 1 and 100.");
            }
            return new InvitationPageQuery(page, size);
        } catch (NumberFormatException exception) {
            throw new MalformedDealRequestException();
        }
    }

    long offset() { return Math.multiplyExact((long) page, size); }

    private static DealValidationException validation(String field, FieldErrorCode code, String message) {
        return new DealValidationException(field, code, message);
    }
}
