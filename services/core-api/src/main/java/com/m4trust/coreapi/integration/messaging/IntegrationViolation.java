package com.m4trust.coreapi.integration.messaging;

/** Signals invalid integration traffic that must not mutate business state or reach DLQ with payload. */
public class IntegrationViolation extends RuntimeException {
}
