# M4Trust Agent Entry Point

Use this file only as a routing entrypoint. Detailed architecture belongs in the accepted ADRs.

## Language

- Speak with the user in Turkish.
- Planner–implementer communication is in English.

## Read by role

Planner:
1. Inspect the latest repository state.
2. Read `docs/agent/WORKFLOW.md` and `docs/agent/CURRENT.md`.
3. Check `docs/plan/ready/` for the approved slice plan; derive tasks from it when one exists.
4. Use `architecture-decisions/ADR-INDEX.md` to load only the ADR sections relevant to the current work. 
5. When using subagents don't make pooling continuously wait for response before wasting any tokens

Implementer:
1. Read this file.
2. Start with the files and ADR sections referenced by the planner.
3. Expand to nearby code or additional documentation only when required to implement or validate the task correctly.
4. Read the nearest module-level `AGENTS.md` when one exists.
5. Inspect the current branch and nearby code before changing anything.
6. Use frontend-skill if available for new frontend implementations

## Common rules

- Accepted ADRs are authoritative when a conflict exists.
- If a change hits an item in `architecture-decisions/FORBIDDEN.md`, stop and escalate; do not build a workaround.
- Work on a feature branch unless the user explicitly requests otherwise.
- Keep tasks focused; avoid unrelated refactors and speculative abstractions.
- Keep automated tests minimal and validate the behavior relevant to the task.
- Surface genuine architectural conflicts instead of silently inventing new rules.
