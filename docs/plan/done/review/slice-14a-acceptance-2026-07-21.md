# Slice 14A Acceptance Record — 21 July 2026

## Result

Slice 14A Dispute and Casework Foundation is accepted at implementation HEAD
`feature/14a-dispute-casework-foundation@e30c185733a601014d8ebbc8413b3cc6a1b2c85d`,
merged to `main` by
`0282c0e103a2fd3c0cacd32b11cb639c098b803c`, plus the planner-owned closure
documentation.

ADR-013 remains authoritative. V22 is the accepted Slice 14A migration;
V15–V22 are frozen history and later persistence changes require a new
forward-only migration.

## Accepted capability

- Buyer or seller legal-entity `ADMIN` users can open one active dispute after
  fulfillment has started.
- Buyer/seller `ADMIN` and `MEMBER` users can read and comment; only the
  counterparty `ADMIN` can acknowledge and only the opening entity's `ADMIN`
  can withdraw.
- Other participants and nonparticipants receive party-only, non-disclosing
  behavior, including actor-aware Deal lifecycle projection.
- Opening atomically retains immutable fulfillment, milestone, finalized
  evidence, and already-successful video-analysis provenance.
- Comments are append-only; case mutations are versioned, idempotent, audited,
  and transactionally isolated.
- Casework has no Deal, fulfillment, evidence, funding, payment, settlement,
  provider, messaging, or AI mutation side effect.
- The generated-type-driven frontend provides opening, active/read-only,
  comments, acknowledgement, withdrawal, history, snapshot, stale/error, and
  evidence-access states.

## Planner review

The planner reviewed the approved plan, ADR-013, FORBIDDEN boundaries, merge
ancestry, complete base-to-implementation changed-file set, V22 authority, and
the material casework service/frontend paths. The reviewed diff is
`dbcad17949b9063b9ef385a858f728d1d0f94536...e30c185`.

No scope expansion into resolution, cancellation, settlement, release, refund,
provider, messaging, deployment, or AI-triggered casework was accepted.

## Recorded automated validation

The implementer reported:

- contract validator — PASS: 21 schemas and 13 fixtures;
- Core API full `mvn verify` — PASS: 331 tests, 0 failures/errors;
- focused migration, authorization, idempotency, concurrency, lifecycle,
  Slice 12/13, Deal, payment, and architecture matrix — PASS;
- frontend typecheck and production build — PASS;
- V15–V21 byte history and AI/messaging contract boundaries — unchanged; and
- `git diff --check` — PASS.

At the user's explicit direction, the planner did not rerun automated commands
during closure. These results are recorded implementer evidence, not a new
planner execution.

## Accepted material deviation and browser debt

The planner-owned Section 6 real-browser acceptance was not run. On 21 July
2026 the user explicitly directed the planner to close Slice 14A and transfer
the complete browser matrix to the next relevant Slice 14B phase.

This is an explicit accepted deviation, not evidence that browser acceptance
passed. The future run must cover the complete Section 6 matrix from the
archived Slice 14A plan, including:

1. genuine cross-tenant buyer/seller ADMIN and MEMBER authorization;
2. open, replay/concurrent-open conflict, comment, acknowledge, refresh, stale
   recovery, pagination, and withdraw through the real UI and Core API;
3. party-only `DISPUTE` lifecycle and non-disclosing unrelated-participant
   behavior;
4. immutable opening evidence/video snapshot under later evidence and video
   changes;
5. unchanged fulfillment/evidence/video actions and absence of unrelated
   business side effects; and
6. visible retirement of the inherited Slice 13 historical VIDEO/MP4
   advisory-panel debt.

## Accepted boundaries

- V22 is accepted; V15–V22 must not be rewritten.
- `casework` owns dispute state, snapshot, comments, authorization, and public
  behavior through narrow module ports.
- `RESOLVED`, assignment, operator authority, ACTIVE cancellation,
  settlement/release/refund, provider work, notifications, messaging, and
  deployment remain outside Slice 14A.
- Slice 14B remains gated planning work and receives no implementation
  authorization from this acceptance.
