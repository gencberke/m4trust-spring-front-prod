# AI integration ownership and asynchronous contracts

## Status

Proposed — pending independent acceptance.

## Context

AI capability is owned by a separate service/repository. The M4Trust repository
owns its business boundary and shared contracts, not provider, model, prompt,
worker implementation or AI deployment decisions.

## Decisions

### Ownership

- Core API remains the authority for users, authorization, workflow state,
  payment, ratification, dispute and business acceptance.
- The AI owner controls provider SDKs, models, prompts, worker internals,
  evaluation and AI runtime/deployment choices.
- The frontend never calls the AI service directly and never owns AI job
  lifecycle authority.
- Spring does not depend on model-native payloads or provider SDK semantics.
- The two repositories do not share a business database or access each other's
  private tables.

### Contract boundary

- Use committed AsyncAPI/JSON Schema and versioned shared examples as the
  integration source of truth.
- Exchange requests and results asynchronously through the accepted messaging
  boundary; do not add synchronous inference endpoints for business flows.
- Send object references and scoped access material, never raw binary objects,
  through the broker.
- Validate contract-visible messages at the boundary and preserve unknown or
  invalid input as an explicit failure path.
- Main-repository changes that alter shared bytes require contract-first review
  and external owner coordination.

### Delivery semantics

- Assume at-least-once delivery.
- Make result consumption duplicate-safe, idempotent and safe when results arrive
  late relative to the business workflow.
- Treat a completed technical result as advisory input, not automatic business
  acceptance or lifecycle mutation.
- Require an authorized human/business decision before an AI result creates a
  binding rule, commitment or state transition.
- Keep technical retry ownership with the service that owns it while preserving
  contract-visible semantics.

### Data and privacy

- Minimize request data and use presigned/scoped object access where applicable.
- Do not place provider credentials, user session material or unnecessary PII in
  events, examples, browser code or logs.
- Retain data, redaction and model-output handling only according to accepted
  product and operational policy; this ADR does not invent an AI retention rule.

### Review checkpoints

- Confirm a changed message is validated against committed shared schema.
- Confirm a request contains a reference rather than embedded object binary.
- Confirm a result cannot create business acceptance without an authorized step.
- Confirm duplicate delivery leaves one durable business effect.
- Confirm a late result cannot resurrect or corrupt a closed workflow.
- Confirm Core code does not parse provider-native model output.
- Confirm browser code does not know AI service topology.
- Confirm logs and examples omit credentials and unnecessary PII.
- Confirm AI-owner implementation choices stay outside this repository's scope.
- Escalate any proposed synchronous inference or shared-database path.

### Boundaries not owned here

- Model quality, prompt design and evaluation are not Core API policy.
- Worker deployment and provider credentials are not frontend concerns.
- AI completion does not decide commercial, dispute or payment outcomes.
- Object retention policy requires accepted product/operational authority.
- Contract compatibility remains a shared review responsibility.
- External owner unavailability is reported rather than bypassed locally.

## Non-negotiables

- No direct frontend-to-AI integration.
- No shared business database or AI-side business mutation.
- No synchronous user-request inference path for business completion.
- No raw binary broker payload or model/provider coupling in Core domain logic.
- No automatic business acceptance from a technical AI completion.

## Consequences

The main repository validates its request publish and result consume boundaries,
including duplicate/late-result safety. It does not duplicate FastAPI unit,
provider, model or deployment tests.

## Change triggers

Revise this ADR when ownership, messaging transport, shared-contract process,
delivery semantics or AI trust boundaries change.
