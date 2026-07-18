package com.m4trust.coreapi.idempotency;

/**
 * Raised when an actor reuses an idempotency key for the same operation with a
 * different canonical request. HTTP adapters map this to IDEMPOTENCY_KEY_REUSED.
 */
public final class IdempotencyKeyReusedException extends RuntimeException {

    public IdempotencyKeyReusedException() {
        super("Idempotency key was already used for a different request");
    }
}
