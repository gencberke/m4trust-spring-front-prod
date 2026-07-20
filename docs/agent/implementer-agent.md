# Implementer Agent Workflow

Implement one planner task packet at a time. The user owns handoffs; do not spawn,
contact, or wait for a planner. Speak Turkish; write `req-review.md` and the final
report in English. This file is self-contained.

## Accept the task

Start only when the packet contains `Task`, `Revision`, `Plan`, `Phases`, `Branch`,
`Base`, `Goal`, `Direction`, `Boundaries`, `Done when`, and `Validation`.

Read the ready plan, assigned phases, referenced ADR sections, and
`architecture-decisions/FORBIDDEN.md`; inspect relevant code before editing.

If the packet conflicts with the plan, expands scope, reaches a forbidden item,
or needs an unmade decision, record it in `req-review.md` and stop. Never invent
a workaround or silently choose new scope.

## Mandatory branch isolation

- Never implement on `main`, `master`, or the packet's base branch.
- Work only on the exact feature branch named in `Branch`.
- Create it from the exact `Base` when it does not exist.
- Verify the current branch and base SHA before editing.
- Preserve unrelated changes; never reset or overwrite them.
- Do not merge, push, or open a PR unless the user asks.

If branch isolation cannot be established safely, report `BLOCKED` before changing files.

## Iterate through phases

Execute phases strictly in plan order. For every phase:

1. Re-read its outcome, direction, dependencies, and exit checks.
2. Inspect the nearest implementation and tests.
3. Implement only that phase.
4. Run its exit checks and minimum risk-proportional tests.
5. Update `docs/agent/req-review.md` immediately.
6. Continue only when the phase is `DONE`.

Use contract-first order for public/shared API changes. Preserve ownership,
authorization, compatibility, transaction/external-call boundaries, lock order,
idempotency, immutable history, and forward-only migrations.

Do not edit unrelated code, accepted migrations, the ready plan, or
`docs/agent/CURRENT.md`. Do not perform planner-owned browser acceptance unless
the task packet explicitly assigns it.

## Maintain the review inbox

Create or replace `docs/agent/req-review.md` when work starts. Update it after
every phase using:

```text
# Review Request
Task: <NN-TXX>
Revision: <integer>
Plan: <ready plan path>
Phases: <assigned phases>
Status: IN_PROGRESS | COMPLETED | PARTIAL | BLOCKED
Branch: <feature branch>
Base: <branch@sha>
Plan completion claim: YES | NO

## Phase outcomes
- P1 — DONE — <short implementation and test evidence>
- P2 — IN_PROGRESS | PARTIAL | NOT_STARTED — <short note>

## Validation
- `<check>` — PASS | FAIL | NOT_RUN

## Decisions needed
- None
or
- <exact decision, conflict, or missing authority>

## Deviation or risk
- None
or
- <material deviation or risk>
```

Keep notes short: `P1 — DONE — ...`. If a decision is needed, add it under
`Decisions needed`, set `BLOCKED` or `PARTIAL`, and stop before undecided work.
Do not paste logs, diffs, or changed-file inventories.

## Finish the task

After all phases are `DONE`, run the packet's full validation and a fast final
check: targeted tests, generated artifacts when applicable, `git diff --check`,
`git status --short`, and the base-to-HEAD file list.

Set `COMPLETED` only when every assigned phase and implementer check passes.
This is not planner acceptance; use `Plan completion claim: NO` while
planner-owned acceptance remains.

Include final `req-review.md` in the feature branch's final commit without staging
unrelated files. Return status, task/revision, plan/phases, branch/commit,
review-request path, plan-completion claim, up to five summary bullets,
validation results, and any decision/deviation/risk.
