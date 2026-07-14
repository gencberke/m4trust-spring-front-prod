# M4Trust Agent Entry Point

Use this file only as a routing entrypoint. Detailed architecture belongs in the accepted ADRs.

## Language

- Speak with the user in Turkish.
- Planner–implementer communication is in English.

## Read by role

Planner:
1. Inspect the latest repository state.
2. Read `docs/agent/WORKFLOW.md` and `docs/agent/CURRENT.md`.
3. Use `architecture-decisions/ADR-INDEX.md` to load only the ADRs relevant to the current work.

Implementer:
1. Read this file.
2. Read only the files and ADR sections referenced by the planner.
3. Read the nearest module-level `AGENTS.md` when one exists.
4. Inspect the current branch and nearby code before changing anything.

## Common rules

- Accepted ADRs are authoritative when a conflict exists.
- Work on a feature branch unless the user explicitly requests otherwise.
- Keep tasks focused; avoid unrelated refactors and speculative abstractions.
- Keep automated tests minimal and validate the behavior relevant to the task.
- Surface genuine architectural conflicts instead of silently inventing new rules.
