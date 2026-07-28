# Backend ownership and business integrity

## Status

Proposed — pending independent acceptance.

## Context

The Core API is a Spring modular monolith and the authority for business state.
It must preserve tenant isolation and durable financial/workflow integrity while
integrating with storage, messaging, payment and AI boundaries.

## Decisions

### Modular ownership

- Organize modules so API, domain and infrastructure responsibilities are
  visible and independently testable.
- Domain code has no first-party dependency on an `api` or `infra` package.
- Cross-module access uses explicit ports, identifiers, events or projections;
  modules do not share repository access or persistence entities as shortcuts.
- A repository is accessed only by the module that owns it.
- Public transport DTOs and Problem Details live at the API boundary, not in the
  domain model.
- Avoid cyclic top-level module dependencies.

### Authorization and isolation

- Authenticate with the server-side session model described by Frontend and
  Deployment authority.
- Enforce user, tenant, active legal entity, participant relationship and
  operation authorization in application behavior, not only controllers.
- Treat legal entity selection as validated context rather than proof.
- Preserve non-disclosure where a hidden resource must not become discoverable.
- Visibility and mutation permission are separate decisions.
- Keep lifecycle actions explicit; do not expose generic state mutation.

### State and data integrity

- Use explicit immutable snapshots for accepted commitments and ratification
  material; a changed commitment requires a new versioned decision path.
- Use optimistic versioning or an equivalent explicit concurrency rule for
  mutable aggregates; silent last-write-wins is forbidden.
- Represent money in integer minor units with ISO currency and percentages in
  integer basis points; never use floating point for business amounts.
- Use RFC 3339 UTC timestamps and explicit calendar-date values at interfaces.
- Keep state transition rules in domain/application logic and test complex rules
  with compact parameterized cases.
- Preserve idempotency for risky commands and reconcile unknown external
  outcomes before issuing a potentially duplicate action.

### Transactions and external boundaries

- Write business mutation and audit atomically in one transaction.
- Use an outbox in the same transaction only where an accepted operation owns a
  durable event or dispatch obligation.
- Do not make network, provider or AI calls while a database transaction is open.
- Make inbox/result consumption duplicate-safe and safe for late delivery.
- Keep external provider behavior behind infrastructure adapters and report
  explicit ambiguity rather than assuming success.
- Audit is append-only and is not a replacement for security logging.

### API and contracts

- Commit public OpenAPI before implementation changes.
- Return resource representations directly unless a contract says otherwise.
- Use RFC 9457 Problem Details with stable application code and correlation data.
- Keep parse, semantic validation, conflict, authorization and non-disclosure
  responses distinct according to the committed contract.
- Use expected-version and idempotency headers only where the contract specifies
  them; do not invent alternate wire conventions in code.

### Review checkpoints

- Identify the owning module before accessing persistence.
- Verify each mutating operation has an application-level authorization decision.
- Verify hidden resources remain non-disclosing where required.
- Verify an external ambiguity follows reconciliation rather than duplicate dispatch.
- Verify audit and the protected mutation share transaction outcome.
- Verify a new event has an explicit durable-dispatch owner before adding outbox work.
- Verify an idempotent command has stable replay and mismatch behavior.
- Verify a mutable aggregate has an explicit concurrency rule.
- Verify public errors and success bodies follow their committed contract.
- Escalate any required cross-module persistence shortcut.

## Non-negotiables

- No cross-module repository or JPA entity shortcut.
- No domain dependency on first-party API or infrastructure packages.
- No authorization decision only in the frontend or controller.
- No external call inside an open business transaction.
- No silent concurrent overwrite, floating-money calculation or mutable audit row.

## Consequences

The Core API stays the sole business authority while adapters remain replaceable.
Tests focus on critical flows, isolation, integrity and boundary behavior rather
than module-local implementation detail.

## Change triggers

Revise this ADR when modular ownership, security context, transaction model,
business-state authority or public API conventions change.
