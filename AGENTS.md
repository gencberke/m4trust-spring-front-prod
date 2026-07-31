# M4Trust Agent Entry Point

Use this file only as a role router. Detailed architecture belongs in the
accepted ADRs.

## Language

- Speak with the user in Turkish.
- Task packets and implementer reports are written in English.

## Start here

Cold-start discovery — read these before role-specific files:

- Local bootstrap: [`docs/DEVELOPMENT.md`](docs/DEVELOPMENT.md)
- Light validation map: [`docs/VALIDATION.md`](docs/VALIDATION.md)
- Project state: [`docs/plan/CURRENT.md`](docs/plan/CURRENT.md)
- What's next: [`docs/ROADMAP.md`](docs/ROADMAP.md)
- Plan workflow: [`docs/plan/README.md`](docs/plan/README.md)
- ADR authority: [`architecture-decisions/ADR-INDEX.md`](architecture-decisions/ADR-INDEX.md)
- Repo map (generated): [`docs/agent/repo-map.md`](docs/agent/repo-map.md)

Hackathon-era plans, gate decisions and review records are archived under
[`docs/history/hackathon-2026-07/`](docs/history/hackathon-2026-07/). They are
historical context, never current project state.

## Read by role

Planner:
1. Read `docs/agent/planner-agent.md`.
2. Read `docs/plan/CURRENT.md` when planning or accepting project-state
   changes.
3. The user owns every handoff to and from the implementer.

Implementer:
1. Read `docs/agent/implementer-agent.md`.
2. Do not move plans or update accepted project state.

## Common rules

- The ADR index defines the authority order. Accepted ADRs are authoritative
  when a conflict exists.
- If work conflicts with an ADR non-negotiable, stop and escalate; do not build
  a workaround.
- Work on a feature branch unless the user explicitly requests otherwise.
- Keep changes focused; avoid unrelated refactors and speculative abstractions.
- Preserve unrelated working-tree changes.
- Keep automated tests proportional to the behavior being changed.
- Surface genuine architectural conflicts instead of silently inventing rules.
