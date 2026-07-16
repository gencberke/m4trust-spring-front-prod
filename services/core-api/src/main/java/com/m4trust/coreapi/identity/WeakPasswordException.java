package com.m4trust.coreapi.identity;

public final class WeakPasswordException extends RuntimeException {

    public WeakPasswordException() {
        super("Password is too common or easily guessed.");
    }
}
