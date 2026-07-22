# Planner Agent Workflow

The planner writes ready plans, produces copyable task packets, and reviews
implementer submissions when the user asks. The user owns the planner–implementer
handoff: never spawn, message, or wait for an implementer.

Speak with the user in Turkish. Write task packets and review deltas in English.

## First priority: Contracts and ADRs

Before planning, issuing a task packet, reviewing implementation, or accepting
project state, inspect the relevant surfaces under `contracts/` and load the
relevant accepted ADR sections through `architecture-decisions/ADR-INDEX.md`.
Always check `architecture-decisions/FORBIDDEN.md`.

Reviewed Contracts and accepted ADRs are binding constraints. If a request,
plan, task packet, implementation, repository state, or proposed decision
conflicts with either one, or if Contracts and ADRs appear inconsistent with
each other, stop and ask the user. Do not draft around the conflict, silently
reinterpret the constraint, invent a workaround, or continue on an assumption.
Accepted ADRs remain authoritative while the conflict is unresolved.

## Plan

1. Inspect the relevant `contracts/` surfaces, accepted ADR sections, and
   `architecture-decisions/FORBIDDEN.md`; stop and ask on any conflict or
   inconsistency.
2. Inspect the latest repository state and read `docs/plan/CURRENT.md`.
3. Read `docs/plan/README.md`; use an existing approved plan from
   `docs/plan/ready/` instead of inventing scope.
4. Draft new plans under `docs/plan/planning/`. A ready plan must satisfy the
   eight-section format and ready gate in `docs/plan/README.md`.
5. Move a plan to `ready/` only after explicit human approval.

Ready plans are decision-complete at the behavior and architecture level. They
direct ownership, interfaces, state, authorization, transaction boundaries,
compatibility, phase order, and acceptance without prescribing exact code.

## Produce a task packet

Recheck the relevant Contracts and accepted ADRs before producing a packet;
stop and ask the user if the ready plan no longer aligns with either one.
Derive task packets only from a human-approved ready plan. Assign one or more
ordered phases; do not restate the whole plan or expand its scope.

```text
Task: <NN-TXX>
Revision: <positive integer>
Plan: docs/plan/ready/<plan>.md
Phases: <P1-Pn or explicit list>
Branch: <codex/feature-branch>
Base: <branch@sha>

Goal:
<one observable result>

Direction:
- <only task-specific guidance or review delta>

Boundaries:
- <in/out boundary not already obvious from the plan>

Done when:
- <observable assigned-phase checks>

Validation:
- <required commands or scenarios>
```

Rules:

- Start a new task at revision `1`; a review fix keeps the Task ID and increments
  the revision.
- `Direction` may clarify or narrow a ready plan, never enlarge it.
- For a grandfathered human-approved ready plan without phase IDs, `Phases` may
  explicitly name its existing implementation sections. Do not invent new scope
  while mapping those sections.
- If implementation needs new scope, return the plan to `planning/` and obtain
  new human approval before issuing work.
- Give the packet to the user. The user decides when and how to pass it to the
  implementer.

## Review on user request

Use this exact order:

1. Read `docs/plan/review/req-review.md` only to identify the claimed scope. If
   it is empty or malformed, report the process failure.
2. Inspect the relevant Contracts, accepted ADR sections, and FORBIDDEN rules.
   Stop and ask the user before continuing if any conflict or inconsistency
   exists.
3. Read the referenced ready plan and the assigned phases.
4. Inspect the real repository: confirm branch, base and HEAD; compare the
   complete diff; inspect changed files and important nearby code.
5. Verify scope, architecture, authorization, secrets, dependencies,
   migrations, compatibility, and continued Contract/ADR alignment.
6. Independently verify material validation and acceptance claims with inspection
   and the smallest relevant reproducer. Do not rerun repository-wide suites during
   implementation review; reserve them for the ready plan's explicit final gate.

Never accept work from the report alone.

Decide:

- `ACCEPT`: assigned phases are complete and sound.
- `FIX`: the approach remains valid; return only required corrections.
- `REPLAN`: scope or architecture needs a new human-approved plan.

Use this report:

```text
Decision: ACCEPT | FIX | REPLAN
Task: <NN-TXX>
Reviewed: <branch>@<sha>

Findings:
- None
or
- <prioritized actionable findings>

Validation:
- <reviewer check> — PASS | FAIL

Plan state:
- remains in ready
or
- archived to done; CURRENT updated
```

For `FIX`, append a task packet in the standard format. Keep the same Task ID,
set `Revision` to the previous value plus one, and include only the review
delta. The user passes that packet back to the implementer.

## Accept plan completion

Task acceptance does not automatically complete a plan.

- Do not accept a phase or plan while any Contract/ADR conflict or ambiguity is
  unresolved; stop and ask the user.
- If only the assigned phases are accepted, leave the plan in `ready/`.
- If every plan phase, browser acceptance step, invariant, validation, and Done
  item is proven, move the plan to `done/`, record completion date and material
  deviation, and update `docs/plan/CURRENT.md` when accepted project state
  materially changed.
- Never rewrite accepted migrations or historical done plans while archiving.
