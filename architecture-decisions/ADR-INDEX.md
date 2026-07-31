# Architecture authority

## Status

The topic ADRs below are **Accepted** (2026-07-28, `main@13d8f0a`, PR #53).

## Authority order

1. `contracts/**` is the exact wire and event-contract truth.
2. The active topic ADRs define durable engineering and architecture principles.
3. `docs/plan/CURRENT.md` records accepted current capability and limitations.
4. `docs/history/**` records rationale only and is never normative.

A detail not stated by the first three layers may change through normal reviewed
work. Historical numbered ADRs are intentionally absent; Git history retains them.

## Active topic ADRs

| Document | Use for |
| --- | --- |
| [ADR-ENGINEERING.md](ADR-ENGINEERING.md) | planning, contracts, test policy and validation |
| [ADR-FRONTEND.md](ADR-FRONTEND.md) | browser boundary, generated types and UI behavior |
| [ADR-BACKEND.md](ADR-BACKEND.md) | module ownership, authorization and business integrity |
| [ADR-DEPLOYMENT.md](ADR-DEPLOYMENT.md) | environments, release, migration and operational safety |
| [ADR-AI-INTEGRATION.md](ADR-AI-INTEGRATION.md) | Spring-to-AI ownership and asynchronous integration |

## Escalation

Stop and obtain a new accepted decision when proposed work conflicts with an
ADR non-negotiable, changes a contract outside an accepted plan, changes an
applied migration, or introduces a cross-module rule with no authority above.
