package com.m4trust.coreapi.integration.payment;

/** Deterministic sandbox outcomes consumed one-per-new-operation from startup config (ADR-010 §2.6). */
enum SandboxScenario {
    SUCCESS,
    DECLINE,
    TIMEOUT_THEN_SUCCESS
}
