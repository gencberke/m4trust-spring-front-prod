# Planner–Reviewer and Implementer Workflow

This workflow keeps architectural context with one planner-reviewer while giving each implementer a small, focused task context.

## Recommended profiles

- Planner–reviewer: GPT-5.6 Sol, medium effort.
- Implementer: GPT-5.6 Luna or Terra, medium effort.

The planner selects the implementer profile based on task fit and observed results. Do not encode unsupported assumptions about a model name as architectural policy.

## Ownership model

The planner-reviewer:

- understands the current repository and accepted ADR boundaries,
- shapes the next smallest useful task,
- selects only the required context references,
- gives limited technical direction and important tradeoffs,
- spawns one implementer on a feature branch,
- reviews the actual branch and diff,
- decides `ACCEPT`, `FIX`, or `REPLAN`.

The implementer:

- receives a clean task context rather than the full user conversation,
- reads only the referenced repository guidance and nearby code,
- uses engineering judgment for implementation details,
- avoids unrelated work,
- validates the observable behavior,
- returns a compact report in Turkish.

## Context budget rules

- Do not paste ADR contents into task prompts.
- Reference `AGENTS.md`, the nearest module guide, relevant ADR headings, contracts, and nearby code.
- Repeat a global rule only when it directly affects the task.
- Give two to five high-value technical directions, not a class-by-class implementation recipe.
- Keep exclusions only where scope confusion is likely.
- The implementer report is an index to the work, not a substitute for reviewing the repository.
- Correction prompts contain only the delta; do not resend the original task.

## Implementer task prompt

Write the prompt in English and require the implementer to respond in Turkish.

```text
You are implementing one focused task in the M4Trust repository. Respond in Turkish.

## Goal
<Describe the observable result in one to three sentences.>

## Read first
- `AGENTS.md`
- `<nearest module AGENTS.md, when present>`
- `<only relevant ADR headings, contract files, or nearby code>`

Inspect the current branch and nearby implementation before changing code. Load unrelated ADRs only if you discover a genuine architectural conflict.

## Technical direction
- <Two to five high-value design hints or tradeoffs>
- <Prefer a framework-native or existing repository approach where relevant>
- <Name an approach to avoid only when the risk is real>

Use your own engineering judgment for code structure and implementation details.

## Scope
In: <compact scope>
Out: <only important exclusions>

## Done when
- <Three to six observable acceptance checks>
- Keep automated tests minimal and focused on critical behavior.
- Run the relevant build and runtime validation.

## Delivery
Work on `<feature-branch>`. Do not merge into `main`.
Return only the compact completion report below.
```

The planner should normally keep this prompt compact. Add detail only when it prevents a likely wrong architectural or product decision.

## Implementer completion report

```text
## Durum
`COMPLETED` | `PARTIAL` | `BLOCKED`

## Teslim
- Branch: `<branch>`
- Commit: `<sha or NOT_COMMITTED>`

## Değişiklik özeti
- <At most five short behavioral bullets>

## Doğrulama
- `<command or runtime check>` — PASS | FAIL

## Sapma veya risk
- `None`
  or
- <Only a task deviation, incomplete behavior, blocker, or material risk>
```

Do not include long reasoning, full logs, copied diffs, or a file-by-file narration unless the planner specifically requests it.

## Planner review protocol

The planner does not approve work from the report alone.

1. Confirm the branch and commit.
2. Compare the feature branch with its base branch.
3. Inspect changed files and important nearby code.
4. Check scope, ADR boundaries, secrets, unnecessary dependencies, and speculative abstractions.
5. Verify the claimed build/runtime checks when material.
6. Evaluate the original observable completion checks.
7. Return one decision.

### ACCEPT

The task meets its observable checks and does not introduce a material architectural or scope problem. Update `docs/agent/CURRENT.md` only when project state materially changed, then shape the next task.

### FIX

The existing approach is usable but requires a small, targeted correction. Keep the same branch and send only the required delta.

```text
Decision: FIX

Keep the existing branch and implementation.
Make only these corrections:
1. <specific observable correction>
2. <specific observable correction>

Do not refactor unrelated code.
Re-run: <relevant checks>.
Return the same compact completion report in Turkish.
```

### REPLAN

The task boundary, architecture direction, or chosen approach is materially wrong. Do not accumulate patches on a poor foundation. Re-read the relevant repository state, redefine the task, and spawn a clean implementation attempt when appropriate.

## Planner handoff state

`docs/agent/CURRENT.md` is the compact continuity record for a new planner or a new conversation. It should contain facts, accepted progress, active work, blockers, and the next likely capability—never hidden reasoning or a conversation transcript.
