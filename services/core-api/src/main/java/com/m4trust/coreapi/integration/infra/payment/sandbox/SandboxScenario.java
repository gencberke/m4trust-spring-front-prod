package com.m4trust.coreapi.integration.infra.payment.sandbox;

/** Deterministic sandbox outcomes consumed one-per-new-operation from startup config. */
enum SandboxScenario {
  SUCCESS,
  DECLINE,
  TIMEOUT_THEN_SUCCESS
}
