# M4Trust Agent Entry Point

Use this file only as a role router. Detailed architecture belongs in the
accepted ADRs.

## Language

- Speak with the user in Turkish.
- Task packets and implementer reports are written in English.

## Read by role

Planner:
1. Read `docs/agent/planner-agent.md`.
2. Read `docs/plan/CURRENT.md` when planning or accepting project-state
   changes.
3. The user owns every handoff to and from the implementer.

Implementer:
1. Read `docs/agent/implementer-agent.md`.
2. Accept work only through the task-packet format defined there.
3. Do not move plans or update accepted project state.

## Common rules

- Accepted ADRs are authoritative when a conflict exists.
- If work hits `architecture-decisions/FORBIDDEN.md`, stop and escalate; do not
  build a workaround.
- Work on a feature branch unless the user explicitly requests otherwise.
- Keep changes focused; avoid unrelated refactors and speculative abstractions.
- Preserve unrelated working-tree changes.
- Keep automated tests proportional to the behavior being changed.
- Surface genuine architectural conflicts instead of silently inventing rules.
