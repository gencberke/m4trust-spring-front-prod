# Deployment and operational safety

## Status

Accepted (2026-07-28). Merged `main@13d8f0a` (PR #53).

## Context

Runtime configuration and release operations can invalidate otherwise correct
application behavior. This ADR defines durable deployment safety principles;
the accepted current environment and temporary demo limitations live in
`docs/plan/CURRENT.md`.

## Decisions

### Environments and exposure

- Keep local, staging and production resources isolated.
- Expose the web boundary publicly only where required; Core API, databases,
  brokers and object storage remain private except through explicit boundaries.
- Preserve same-origin browser-to-Core routing and do not expose private service
  addresses to client bundles.
- Keep configuration environment-specific and fail closed when required values
  are missing, unsafe or production-like mock settings are enabled.
- Never store secrets in the repository, container image or frontend bundle.

### Release identity

- Build immutable artifacts from an exact source revision.
- Record the deployed source/artifact identity; do not deploy mutable `latest`
  labels as release identity.
- Keep image creation and release-manifest identity acyclic.
- Run release/artifact validation separately from the fast developer loop.
- Preserve container image and routing smoke checks for changes that affect their
  release surface.

### Data and migration safety

- Apply Flyway migrations forward only.
- Never alter, delete or rename an applied migration.
- Require additive/compatible rollout planning for schema changes and use
  expand–contract where a breaking evolution needs coexistence.
- Run migrations before dependent application behavior is declared ready.
- Roll back application code only to an image compatible with the resulting
  schema; database rollback is not assumed.
- Keep migration checksums and CI history protection as integrity evidence.

### Runtime operation

- Emit structured logs and include correlation and release identity where
  available.
- Separate liveness/readiness from business health assumptions.
- Define recovery, backup and operational claims only when accepted current state
  documents the evidence; absence of a claim is not implicit readiness.
- Treat destructive persistent-data operations as requiring explicit owner
  approval and a recoverable procedure.
- Keep mock, sandbox and demo integrations clearly non-production unless current
  accepted state explicitly says otherwise.

### Review checkpoints

- Confirm an environment has no unintended shared state.
- Confirm every public listener is intentional and documented.
- Confirm required configuration rejects unsafe startup.
- Confirm the release points to an exact source or immutable artifact identity.
- Confirm an applied migration has no candidate diff.
- Confirm a new migration is forward-compatible at rollout.
- Confirm a rollback image is schema-compatible.
- Confirm observability does not disclose credentials or private payloads.
- Confirm demo evidence is not described as broad production readiness.
- Escalate persistent-data destruction before issuing an operational command.

### Boundaries not owned here

- Business lifecycle and authorization rules remain Backend concerns.
- Browser rendering and session-state presentation remain Frontend concerns.
- AI model and worker implementation remain AI-owner concerns.
- Contract bytes remain the contract repository's exact interface authority.
- Provider-specific demo limitations are accepted project state, not defaults.
- A health endpoint does not prove product workflow readiness.

## Non-negotiables

- No secret in source, image or browser artifact.
- No public database, broker or internal Core endpoint by default.
- No changed applied Flyway migration.
- No deployment from an unidentifiable or mutable release artifact.
- No production claim based on untested demo or mock behavior.

## Consequences

Operational safeguards are stable across hosting providers while provider-specific
choices and accepted demo carve-outs stay out of this durable ADR. A deployment
change must show both configuration safety and release identity evidence.

## Change triggers

Revise this ADR when deployment trust boundaries, release identity, migration
policy, environment isolation or operational recovery commitments change.
