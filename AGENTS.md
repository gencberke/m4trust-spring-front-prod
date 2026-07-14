# M4Trust Agent Guide

This file is the default instruction entrypoint for planner, reviewer, and implementer agents working in this repository.

Respond to the user in Turkish. Prompts written for spawned implementer agents must be in English and must instruct the implementer to respond in Turkish.

## Project purpose

M4Trust is being rebuilt from scratch as a React frontend, a Spring Boot modular monolith, PostgreSQL, and a separate FastAPI AI boundary connected asynchronously through RabbitMQ. The legacy repository is only a domain and behavior reference; compatibility with it is not required.

## Non-negotiable boundaries

- React calls only the Spring public API.
- Spring Boot is the only public business backend and the business authority.
- PostgreSQL owned by Spring is the business source of truth.
- FastAPI must not access Spring PostgreSQL or mutate business or payment state.
- AI work is asynchronous through the versioned contracts and RabbitMQ.
- Raw files stay in S3-compatible object storage and are never placed in RabbitMQ messages.
- Business mutation, audit, and outbox records belong in the same PostgreSQL transaction.
- Inbound message handling must be duplicate-safe and use inbox semantics.
- Public Spring APIs follow `/api/v1`, the committed OpenAPI design contract, and RFC 9457 Problem Details.
- The frontend does not calculate lifecycle, permissions, or available actions by itself.
- Production secrets never enter Git, container images, logs, or the frontend bundle.
- Mock AI workers are allowed only for local or controlled staging use, never production.

Accepted ADRs are authoritative when a conflict exists. Implementation details may evolve without a new ADR unless they change a system boundary, data ownership, public contract policy, security model, or deployment topology.

## Context loading protocol

Do not load every ADR by default.

1. Read this file.
2. Read the nearest `AGENTS.md` under the area being changed, when one exists.
3. Read `architecture-decisions/ADR-INDEX.md`.
4. Read only the ADRs or sections relevant to the current task.
5. Inspect the current branch and nearby implementation before proposing or changing code.
6. If the task exposes a genuine architectural conflict, surface it instead of silently inventing a new rule.

The ADR index is a routing aid, not a replacement for the accepted ADR files.

## Delivery principles

- Work in small, runnable vertical increments.
- Prefer a feature branch unless the user explicitly requests work on `main`.
- Keep automated tests minimal and focused on critical invariants.
- The decisive acceptance test is the real frontend flow against the real Spring API and PostgreSQL.
- For AI slices, the RabbitMQ and contract boundary should be real even when the AI provider or worker behavior is mocked.
- Prefer framework-native solutions before custom abstractions.
- Do not create speculative packages, layers, modules, or infrastructure for possible future use.
- Do not broaden the task with unrelated refactors or documentation.
- Validate the actual system behavior relevant to the task, not only compilation.

## Planner-reviewer workflow

The planner-reviewer owns overall context, task shaping, and review. It should give implementers a compact task packet with:

- the observable goal,
- a few context references,
- limited technical direction,
- a clear scope boundary,
- observable completion checks,
- branch and delivery instructions.

The planner should guide important tradeoffs without prescribing every class, method, or file.

The implementer works in a clean task context, uses engineering judgment for code details, and returns a compact completion report. The planner reviews the actual branch, diff, changed files, and validation evidence rather than relying only on the report.

The detailed workflow and templates are in `docs/agent/WORKFLOW.md`.

## Current state

Read `docs/agent/CURRENT.md` for the compact, updateable project status. Keep that file factual and short; it is not a conversation transcript or a long-term roadmap.
