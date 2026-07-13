# M4Trust shared AI contracts

This directory is the initial shared Spring–AI contract foundation for ADR-001 and ADR-002. It defines versioned JSON Schemas, canonical examples, the asynchronous topology, and operational internal API metadata. Spring is the business authority. FastAPI workers consume the request contracts and return canonical M4Trust results; they do not return model-native or provider-specific payloads.

Raw document and video bytes are never carried in RabbitMQ messages. Requests contain a short-lived download reference with a URL and RFC 3339 UTC expiry. Delivery is at-least-once, so `jobId`, `eventId`, `correlationId`, `causationId`, and `idempotencyKey` are explicit and consumers must be idempotent.

## Ownership and review

The M4Trust core/platform team owns the shared envelope, topology, and cross-job contracts. The owning job team proposes job-specific changes. Changes require review from the core/platform owner and the affected Spring and FastAPI owners. Contract changes are reviewed as API changes, including compatibility, fixture updates, and a successful validation run.

## Directory structure

```text
contracts/
  asyncapi/       RabbitMQ topic-exchange topology and message references
  openapi/        Internal operational endpoints only
  schemas/        Draft 2020-12 shared and job-specific schemas
  examples/       Canonical fixtures used by validation and documentation
  scripts/        Lightweight local validation tooling
```

`requirements.txt` contains only the small `jsonschema` dependency needed by the validator. No Spring Boot, FastAPI, broker, registry, code-generation, Docker, Maven, Gradle, or npm runtime integration is part of this foundation.

## Versioning rules

Event names carry the major version: `ai.job.requested.v1`, `ai.job.completed.v1`, `ai.job.failed.v1`, and `ai.job.cancel.requested.v1`. Payload and schema filenames carry semantic versions such as `1.0.0`. `schemaVersion` is semantic-version formatted. Event envelopes use UTC `date-time` values and UUID identifiers.

Closed enum values reject unknown values. Version 1 explicitly defines `UNKNOWN` only where the domain needs an unknown classification. Warning `code` is an open string for forward-compatible advisory additions; warning `severity` remains the closed set `INFO` or `WARNING`.

Defined contract objects use `additionalProperties: false` by default. The envelope `payload` is the one intentional polymorphic boundary: it is constrained by every job-specific event schema. Consumers should deserialize only fields they understand and ignore unknown optional fields when a future compatible schema permits them; producers must not repurpose existing fields or change their meaning.

Money uses integer `amountMinor` plus an ISO-style three-letter currency code. Percentages use integer basis points. `requiresManualReview`, video `advisoryOutcome`, warnings, and technical metadata are advisory or descriptive only. Spring owns final business decisions; video output cannot approve delivery, release payment, or resolve disputes.

## Validate locally

From the repository root:

```powershell
python -m pip install -r contracts/requirements.txt
python contracts/scripts/validate_contracts.py
```

The validator checks every JSON Schema with Draft 2020-12 schema checks, resolves local `$ref` references, and validates every canonical JSON fixture against its intended event schema. It prints each pass/failure and exits non-zero on failure.

## Compatibility and consumers

Spring should select a schema by the event name and declared semantic `schemaVersion`, validate before publishing, and remain the authority for job lifecycle and business outcomes. FastAPI should validate the request, download the referenced source, perform internal processing, and publish only the canonical completed or failed event. Generated transport models, if used later, must remain adapters at the messaging boundary; they must not become domain entities because transport compatibility and business identity/lifecycle have different ownership and change rates.

To add a backward-compatible field, make it optional, document its meaning, update the relevant schema and canonical fixture, keep existing required fields and enum meanings unchanged, and verify old consumers can ignore it. If a new value is required for an existing closed enum, prefer a new schema/event major version unless the enum already defines `UNKNOWN` and the semantics truly fit it.

To introduce a new major version, create a new versioned schema set and event names (for example `...v2`), add parallel AsyncAPI references and fixtures, document the migration and coexistence period, and update capabilities. Do not silently change the meaning of a v1 field or routing key.

## JSON Schema semantic limits

JSON Schema validates shape and local constraints. Runtime semantic validation is still required for:

- `endOffset > startOffset` for source references;
- `endMs > startMs` for video time ranges;
- `attemptNumber <= maxAttempts`;
- deadline freshness and job lifecycle/cancellation races;
- SHA-256 matching the downloaded content;
- URL expiry and authorization; and
- cross-field business consistency, including the fact that advisory AI output cannot authorize a business decision.

## Operational documents

`asyncapi/m4trust-ai-v1.yaml` documents the topic exchanges `m4trust.ai.commands`, `m4trust.ai.events`, and `m4trust.ai.dead-letter`; the agreed queues and routing keys are recorded in both AsyncAPI bindings and the `x-m4trust-topology` summary. `openapi/ai-internal-v1.yaml` exposes only liveness, readiness, capabilities, and contract metadata. It intentionally has no synchronous inference endpoint.
