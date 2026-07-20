# Implementer Agent Workflow

The implementer applies assigned phases from one human-approved ready plan. The
user owns communication with the planner. Do not contact, spawn, or wait for a
planner.

Write the review request and final delivery report in English.

## Read before implementation

1. Read `AGENTS.md` and this file.
2. Parse the user's task packet. Do not start without Task, Revision, Plan,
   Phases, Branch, Base, Goal, Direction, Boundaries, Done when, and Validation.
3. Read the referenced ready plan, focusing on the assigned phases and their
   dependencies.
4. Read the ADR sections referenced by the plan and check
   `architecture-decisions/FORBIDDEN.md`.
5. Confirm the branch/base and inspect the latest repository state, nearby code,
   and the nearest module-level `AGENTS.md` when one exists.

If the task packet conflicts with the ready plan or expands it, stop and report
`BLOCKED`. Scope changes require a replanned and human-approved ready plan.
For a grandfathered ready plan without phase IDs, follow the existing sections
explicitly named in `Phases`; do not reinterpret them as new scope.

## Work through phases

- Turn the assigned phases into an internal implementation checklist and execute
  them in plan order.
- Preserve each phase's outcome, dependencies, architecture direction, and exit
  checks. Exact classes, methods, and local mechanics remain implementation
  choices unless an ADR or contract fixes them.
- Use contract-first sequencing when a public or shared contract changes.
- Keep module ownership, transaction/external-call boundaries, lock order,
  authorization, concurrency, compatibility, and migration rules explicit.
- Do not edit unrelated code, accepted historical migrations, the ready plan,
  or `docs/agent/CURRENT.md`.
- Phase-level commits are allowed. Never merge into `main`.
- Run the task's required validation and the minimum additional checks needed to
  support the delivery claim.

Stop and report rather than invent a workaround when:

- an assigned phase needs a missing product or architecture decision;
- two authoritative instructions conflict;
- the task reaches a FORBIDDEN item;
- safe completion requires scope beyond the ready plan.

## Submission status

- `COMPLETED`: every assigned phase and implementer-owned required validation is
  complete.
- `PARTIAL`: useful work exists, but an assigned phase or required validation is
  incomplete.
- `BLOCKED`: safe progress cannot continue without a decision or scope change.

`COMPLETED` is an implementation claim, not planner acceptance.

Set `Plan completion claim: YES` only when all plan phases, browser acceptance,
invariants, validation, and Done items are proven. If acceptance remains for the
planner, use `NO` even when the assigned task is `COMPLETED`.

## Write the review request

After the assigned phases, replace the entire contents of
`docs/agent/req-review.md` with:

```text
# Review Request

Task: <NN-TXX>
Revision: <integer>
Plan: <ready plan path>
Phases: <assigned phases>
Status: COMPLETED | PARTIAL | BLOCKED
Branch: <branch>
Base: <branch@sha>
Plan completion claim: YES | NO

## Phase outcomes

- P1 — DONE | PARTIAL | NOT_STARTED — <short evidence>

## Validation

- `<command or scenario>` — PASS | FAIL | NOT_RUN — <short reason if needed>

## Deviation or risk

None
or
- <material deviation, blocker or risk>

## Review focus

- <at most three areas that deserve reviewer attention>
```

The file is a single active inbox. Replace it on every submission; Git preserves
prior versions. Do not add full logs, a changed-file inventory, or copied diffs.

Include `req-review.md` in the implementation branch's final commit. Do not put
that commit's SHA inside the file; the user-facing report supplies the real HEAD
after the commit exists. Do not stage unrelated working-tree files.

## Return the final report

```text
Status: COMPLETED | PARTIAL | BLOCKED
Task: <NN-TXX>
Revision: <integer>
Plan: <path>
Phases: <phases>
Branch: <branch>
Commit: <HEAD sha>
Review request: docs/agent/req-review.md
Plan completion claim: YES | NO

Summary:
- <up to five bullets>

Validation:
- <check> — PASS | FAIL | NOT_RUN

Deviation or risk:
- None
or
- <material item>
```
