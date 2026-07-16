package com.m4trust.coreapi.identity;

public final class DuplicateEmailException extends RuntimeException {

    public DuplicateEmailException() {
        super("Normalized email is already registered.");
    }
}
