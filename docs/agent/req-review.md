# Review Request
Task: 11BA-T02
Revision: 2
Plan: docs/plan/ready/11b-a-moka-provider-foundation.md
Phases: A-P3–A-P4
Status: COMPLETED
Branch: codex/11ba-t02
Base: main@25679a8d64cb6f447a76370a2eae1d3fe2824634
Plan completion claim: NO

## Phase outcomes
- A-P3 — DONE — Review delta adds authenticated/bounded pool approve/query probe facts, explicitly non-final, plus contradictory NOT_FOUND fail-closed mapping.
- A-P4 — DONE — A Spring durable relay test uses a counting test-only port decorator over the real external Moka HTTP adapter: success and timeout→two-query recovery preserve one key/initiate and observe no active DB transaction during HTTP.

## Validation
- `mvn -q -Dtest=MokaTransportSafetyTest,MokaEmulatorClientIntegrationTest,MokaPaymentProviderBootstrapGuardTest,SandboxPaymentProviderBootstrapGuardTest,ModuleArchitectureTest,PaymentFundingIntegrationTest test` — PASS
- `git diff --check` — PASS
- `zero contract/frontend/migration diff` — PASS

## Decisions needed
- None

## Deviation or risk
- Emulator evidence remains non-production-only and does not close G1.
