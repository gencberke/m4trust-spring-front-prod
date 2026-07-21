# Slice 11B-A Acceptance — Moka Provider Foundation

- Decision: `ACCEPTED`
- Planner acceptance timestamp: `2026-07-21T21:16:22Z`
- Local timestamp: `2026-07-22T00:16:22+03:00`
- Accepted main baseline: `main@7e773d9`
- Plan: `docs/plan/done/11b-a-moka-provider-foundation.md`
- Authority: local/CI non-production foundation only

## Accepted scope

Slice 11B-A now provides a separately started, deterministic Python HTTP Moka
emulator; bounded CheckKey/authentication and exact money conversion; a safe
Moka funding adapter; and probe-only pool approve/query transport capability.
The existing Slice 11 durable relay uses the real HTTP boundary without moving
provider calls into database transactions or changing public funding behavior.

The accepted vertical check proves:

- success performs one query and one initiate, then projects `FUNDED`;
- timeout remains `UNCONFIRMED`/`PENDING` and blocks a second charge;
- recovery performs two further queries on the same lifetime-fixed provider
  key, reaches `FUNDED`, and never performs a second initiate;
- no observed external HTTP call runs inside an active database transaction;
- the timeout path finishes with three queries and one initiate in total; and
- public funding/deal projections, contracts and migrations remain unchanged.

## Review and validation

The planner reviewed the complete A-P1–A-P6 implementation across the three
phase tasks and returned `FIX` where needed before acceptance:

- `11BA-T01` / PR #38: deterministic external emulator and fixture matrix;
  the review added concurrent-state protection and a non-zero money fixture.
- `11BA-T02` / PR #39: bounded real HTTP adapter/client and durable relay;
  the review added strict not-found mapping, probe-only pool operations and an
  actual relay-to-external-emulator recovery test.
- `11BA-T03` / PR #40: the high-value vertical test gained explicit public
  projection and in-flight charge assertions; planner review returned
  `ACCEPT`.

Focused implementer validation passed:

- Python emulator unit/integration suite: 11 tests.
- Moka transport, external emulator client, Moka/sandbox bootstrap guards,
  module architecture and payment funding integration tests.
- `git diff --check` and zero contract/frontend/migration drift checks.

The planner independently reran the material final
`PaymentFundingIntegrationTest`; it passed with exit code `0`. No browser E2E
or full backend suite was run because Slice 11B-A changes no browser/public
contract behavior and the process/HTTP boundary is the valuable end-to-end
checkpoint.

## Boundary and next gate

This acceptance is not real-provider evidence and does not close G1. It proves
no Moka 3D/callback authority, real duplicate semantics, pool approval
finality, settlement visibility, pending cutoffs or production suitability.
Pool approve/query remains integration-only and unreachable from payment
business services.

The next allowed work is Slice 11B-B phase B-G0: explicitly authorized,
redacted Moka test-environment funding and pool capability probes. No B-P1
implementation task may start until B-G0 evidence revises the 11B-B plan and
that evidence-derived plan receives ready approval.

## Subsequent scope decision

Later on 22 July 2026, the founder selected simulation-only payment/release.
The next-work paragraph above is retained as the acceptance-time record but is
superseded by
`docs/agent/gates/simulation-only-payment-decision-2026-07-22.md`. No real
provider Slice 11B-B phase is actionable.
