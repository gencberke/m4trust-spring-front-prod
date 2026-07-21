# Review Request
Task: 11BA-T02
Revision: 1
Plan: docs/plan/ready/11b-a-moka-provider-foundation.md
Phases: A-P3–A-P4
Status: IN_PROGRESS
Branch: codex/11ba-t02
Base: main@25679a8d64cb6f447a76370a2eae1d3fe2824634
Plan completion claim: NO

## Phase outcomes
- A-P3 — DONE — Bounded runtime-only Moka HTTP client; exact CheckKey/money vectors and external-emulator success/decline/timeout/malformed/late-query tests pass.
- A-P4 — DONE — local-moka profile/config selection, fail-closed startup guards, and the unchanged query-first durable relay were validated with external transport coverage and payment integration tests.

## Validation
- `mvn -q -Dtest=MokaTransportSafetyTest,MokaEmulatorClientIntegrationTest,MokaPaymentProviderBootstrapGuardTest,SandboxPaymentProviderBootstrapGuardTest,ModuleArchitectureTest,PaymentFundingIntegrationTest test` — PASS
- `git diff --check` — PASS
- `zero contract/frontend/migration diff` — PASS

## Decisions needed
- None

## Deviation or risk
- External-emulator test needs a local loopback port; it passed in the permitted test environment. Emulator evidence remains non-production-only and does not close G1.
