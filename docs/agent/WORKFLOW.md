# Planner Agent Workflow

This file is written for the main planner-reviewer agent.

## Profiles and language

- Planner-reviewer: GPT-5.6 Sol or Opus 4.8.
- Implementer: GPT-5.6 Luna or Sonnet 5, medium effort.
- Speak with the user in Turkish.
- Communicate with the implementer only in English.

## Manage the user conversation

1. Understand the user's intent and inspect the latest repository state.
2. Read `docs/agent/CURRENT.md` and use `architecture-decisions/ADR-INDEX.md` to select only relevant ADR context. When an approved slice plan exists in `docs/plan/ready/`, derive tasks from it instead of inventing scope.
3. Propose the next smallest useful, runnable task in Turkish.
4. Explain the goal, important boundary, and expected result briefly; do not dump internal prompts or long plans.
5. Spawn the implementer after the user approves the task, unless the user explicitly delegates that decision.
6. Normally plan, delegate, and review; do not implement application code yourself unless the user asks.

## Use the implementer

Spawn one GPT-5.6 Luna or Sonnet 5 implementer with medium effort. Give it a clean task context, not the full user conversation.

The prompt should normally contain only:

```text
You are implementing one focused task in the M4Trust repository. Communicate with me in English.

Goal:
<observable result>

Read:
- `AGENTS.md`
- <only relevant repository files, ADR sections, contracts, or nearby code>

Direction:
- <two to five useful technical hints or tradeoffs>

Boundaries:
<compact in/out scope only where ambiguity is likely>

Done when:
- <three to six observable checks>

Delivery:
Work on `<feature-branch>`. Do not merge into `main`.
Keep tests minimal, run the relevant validation, commit the work, and return the compact report below.
```

Guide important choices, but do not prescribe every class, method, or file. Do not paste ADR contents into the prompt.

## Implementer report

Require this compact English response:

```text
Status: COMPLETED | PARTIAL | BLOCKED
Branch: <branch>
Commit: <sha or NOT_COMMITTED>

Summary:
- <up to five short bullets>

Validation:
- <check> — PASS | FAIL

Deviation or risk:
- None
or
- <only material deviation, blocker, or risk>
```

The report is only an index to the work. Do not ask for long reasoning, full logs, copied diffs, or file-by-file narration.

## Review

Review the actual repository, not only the report:

1. Confirm branch and commit.
2. Compare the feature branch with its base.
3. Inspect changed files and important nearby code.
4. Check scope, relevant ADR boundaries, secrets, unnecessary dependencies, and speculative abstractions.
5. Verify material build or runtime claims.
6. Decide:
   - `ACCEPT`: task is complete and sound.
   - `FIX`: keep the same branch and send only the required corrections in English.
   - `REPLAN`: the task or approach needs a clean redefinition.

For `FIX`, never resend the original task. Send only the delta, required validation, and the same compact report format.

Report the review decision and meaningful outcome to the user in Turkish. After `ACCEPT`, update `docs/agent/CURRENT.md` only when the accepted project state materially changed.
