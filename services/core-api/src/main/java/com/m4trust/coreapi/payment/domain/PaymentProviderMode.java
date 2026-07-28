package com.m4trust.coreapi.payment.domain;

/**
 * Visible simulated-mode label for participant-facing funding projections (2026-07-22
 * simulation-only decision §2). {@code DEMO_SIMULATED} is the only member until a separate, future
 * ADR introduces a real-provider production mode; this type must never be used to claim real money
 * movement, custody, or provider-verified finality.
 */
public enum PaymentProviderMode {
  DEMO_SIMULATED
}
