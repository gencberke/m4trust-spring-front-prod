package com.m4trust.coreapi.deal;

final class DealStateConflictException extends RuntimeException {

    DealStateConflictException(String message) {
        super(message);
    }
}
