# Planner Runbook — Settlement and Release Roadmap

- Status: active planning/execution guide; not accepted project state
- Owner: planner; human approvals and planner–implementer handoffs remain user-owned
- Opened: 21 July 2026
- Gate authority: `docs/agent/gates/settlement-release-readiness-charter-2026-07-21.md`
- Current accepted state authority: `docs/agent/CURRENT.md`
- Rule: this runbook may track drafts and work-in-progress; `CURRENT.md` may not

## 1. Purpose

Give a fresh planner one operational map for executing and reviewing the
remaining path to Slice 14B without confusing planning evidence, ready plans,
implementer work, planner acceptance or accepted project state.

This guide does not approve a plan, close a gate, authorize credentials, issue a
task packet or replace an ADR. If it conflicts with an accepted ADR, ready plan,
the charter or `docs/agent/WORKFLOW.md`, the authoritative source wins.

## 2. Required execution order

Do not skip forward merely because a later draft exists.

```text
Slice 7 closure / C2-G4b ACCEPTED
→ G2/G3 D-P1–D-P3 ACCEPTED
→ 11B-A ready approval
→ 11B-A external emulator + transport foundation
→ 11B-A planner acceptance

→ simulation-only founder decision / G1-S / G4c ACCEPTED
→ real-provider 11B-B and historical G1 SUPERSEDED

→ ADR-014 R-P1–R-P4 decision phases and human acceptance / C6
→ final 14B R-P5 ready-gate review and human approval / C7
→ Slice 14B implementation phases
→ one final planner-owned staging simulation E2E
→ 14B closure
```

Parallel work is allowed only when it cannot change the same decision or file
surface. Evidence collection may run in parallel with documentation, but no
dependent plan becomes ready until its prerequisite gate is explicitly accepted.

## 3. Roadmap file map

| Stage | Authoritative working file | State before human approval |
| --- | --- | --- |
| Slice 7 | `docs/plan/done/07-staging-deployment.md` | ACCEPTED; preserve evidence |
| Simulation/G2/G3 | `docs/agent/gates/simulation-only-payment-decision-2026-07-22.md` plus historical G2/G3 record | G1-S/G3 accepted; G2 provider route superseded |
| Slice 11B-A | `docs/plan/done/11b-a-moka-provider-foundation.md` | DONE |
| Slice 11B-B/G1 | `docs/plan/planning/11b-b-moka-staging-and-g1.md` | SUPERSEDED; historical planning only |
| ADR-014 | Decision plan: 14B R-P1–R-P4; accepted artifact: `architecture-decisions/ADR-014-Settlement-and-Release.md` | next decision work; not accepted yet |
| Slice 14B | `docs/plan/planning/14b-settlement-and-release.md` | planning until ADR-014 + explicit ready approval |
| Gate status | `docs/agent/gates/settlement-release-readiness-charter-2026-07-21.md` | may track proposed/blocked evidence |
| Accepted state | `docs/agent/CURRENT.md` | update only after material acceptance |

## 4. Gate register and current checkpoint

As of this runbook's creation:

| Gate | Current state | Next proof |
| --- | --- | --- |
| C0 — inherited browser debt | ACCEPTED | Preserve evidence; do not replay by default |
| G4a — Slice 14A | ACCEPTED | Preserve V22 and accepted plan/history |
| C2/G4b — Slice 7 | ACCEPTED | Preserve acceptance evidence; do not replay by default |
| G1-S | ACCEPTED | Preserve explicit simulated mode, query-only terminal proof and production exclusion |
| G2 | ACCEPTED / SUPERSEDED IN PART | Preserve non-claims; use simulation-only authority |
| G3 | ACCEPTED | Preserve v1/v2 compatibility and immutable completedAt decision |
| C3–C4/G4c — simulation transport foundation | ACCEPTED | Preserve accepted 11B-A; never reopen real-provider 11B-B |
| C6 — ADR-014 | BLOCKED | Draft exact simulation settlement/release decisions; explicit human acceptance |
| C7 — 14B ready | BLOCKED | ADR-aligned eight-section plan + explicit human approval |

When a gate changes, update the charter in the same planner change. Update
`CURRENT.md` only if accepted project state materially changed.

## 5. Provisional task/phase map

These mappings organize future task packets. They are not task packets and do
not authorize work. Derive an actual packet only from the then-current
human-approved ready plan and exact base SHA.

### Slice 7

Completed and accepted. No further Slice 7 task packet exists.

### Slice 11B-A

| Proposed task | Plan phases | Observable result |
| --- | --- | --- |
| `11BA-T01` | A-P1–A-P2 | Frozen transport matrix + external HTTP emulator |
| `11BA-T02` | A-P3–A-P4 | Safe Moka client + existing funding port over HTTP |
| `11BA-T03` | A-P5–A-P6 | One valuable boundary check + review handoff |

All three 11B-A tasks are accepted and archived. Do not issue another 11B-A
task; preserve `docs/agent/slice-11b-a-acceptance-2026-07-22.md`.

### Slice 11B-B and historical G1

Superseded on 22 July 2026. No `11B-*` task, credential, provider probe,
real-provider staging activation or browser run may be issued. G1-S and G4c are
accepted only for the simulation scope in the founder decision record.

### Slice 14B

ADR-014 and final ready approval are planner/user decision work, not
implementer task packets:

| Decision phase | Observable result |
| --- | --- |
| R-P1 | Simulation mappings, cardinality and lifetime identity fixed |
| R-P2 | Settlement/release/simulated-terminal states and dispute races fixed |
| R-P3 | Authorization, idempotency, lock order and transactions fixed |
| R-P4 | Decision audit, routing sync and explicit ADR acceptance |
| R-P5 | ADR-aligned 14B plan receives separate human ready approval |

Only after R-P5:

| Proposed task | Plan phase | Observable result |
| --- | --- | --- |
| `14B-T01` | B-P1 | Contract-first simulation design |
| `14B-T02` | B-P2 | Settlement persistence/cardinality/ports |
| `14B-T03` | B-P3 | Eligibility/read projection + explicit durable release intent |
| `14B-T04` | B-P4 | Simulator dispatch + query-first recovery |
| `14B-T05` | B-P5 | Simulated terminality + Deal completion + frontend |
| `14B-T06` | B-P6–B-P7 | Focused hardening + final implementer handoff |
| none | planner acceptance | Final E2E and closure |

No `14B-*` packet exists until ADR-014 and the final plan are explicitly
human-approved.

## 6. Work-state protocol

Use these states in planner notes and evidence records:

- `BLOCKED`: required decision/evidence/accepted predecessor missing.
- `PLANNING`: decisions are being written; no implementation packet.
- `READY`: explicit human approval exists and the plan is in `ready/`.
- `TASK_ACTIVE`: user handed one packet to implementer.
- `REVIEW_REQUESTED`: exact branch/base/HEAD and report exist.
- `FIX`: same task ID, revision incremented, only required delta returned.
- `REPLAN`: scope/architecture changed; return plan to planning and reapprove.
- `PHASE_ACCEPTED`: assigned phases accepted; plan may remain ready.
- `DONE`: every phase, invariant, required browser step and Done item accepted.

Never translate “deployed”, “tests pass”, “report complete” or “branch merged”
directly into `DONE` without the plan closure checklist.

## 7. Planner start/resume checklist

At the start of every roadmap turn:

1. Read `AGENTS.md`, `docs/agent/WORKFLOW.md` and `docs/agent/CURRENT.md`.
2. Read this runbook and the readiness charter.
3. Inspect `git status --short --branch`, worktrees, branch/base/HEAD and
   untracked files. Preserve unrelated changes.
4. Identify the first blocked stage in Section 2; do not plan from a later
   draft when an earlier accepted plan already exists.
5. Read the exact current plan plus only relevant ADR sections and FORBIDDEN.
6. Decide whether the turn is planning, task-packet production, review, external
   evidence collection or closure. Do not blend their authorities.
7. Record simulation profile, scenario and timestamps without raw transport
   payload or financial/provider claims.

## 8. Task-packet and review discipline

Task packets:

- Follow `docs/agent/WORKFLOW.md` exactly and write packets in English.
- Derive only from a human-approved plan in `ready/`.
- Assign ordered phase IDs; do not restate the full plan.
- Include exact `Branch` and `Base`; a new task starts revision 1.
- Direction narrows/clarifies only. New scope requires replan and approval.
- Give the packet to the user; the user owns implementer handoff.

Reviews:

1. Validate `docs/agent/req-review.md` shape and exact HEAD.
2. Read assigned phases and current ready plan.
3. Confirm branch/base/HEAD and inspect the complete diff plus nearby code.
4. Check ADR/FORBIDDEN, authorization, secrets, migrations, dependencies,
   transaction/external-call boundaries and compatibility.
5. Independently verify the material claims proportionally.
6. Return `ACCEPT`, `FIX` or `REPLAN`; never accept from report text alone.

For staging simulation evidence, preserve deployment identity, profile,
scenario, timestamps, call counts and outcome categories. Never copy raw
transport bodies or unnecessary PII into git and never describe it as provider
or money-movement evidence.

## 9. Minimum-test strategy

The default is not “run everything.” Test the changed risk at the lowest layer
that proves it; run broad validation once at the meaningful final handoff.

| Stage | Per-phase validation | Valuable E2E checkpoint |
| --- | --- | --- |
| G1-S/G2/G3 | Document/ADR compatibility review only | None |
| 11B-A | Auth/money mapping, timeout/duplicate/query and transaction boundary | One Core API → external emulator process check; no browser |
| ADR-014 | Decision completeness and evidence traceability | None |
| 14B B-P1–B-P6 | Phase-specific contract/DB/eligibility/simulator/race checks | None |
| 14B B-P7/planner closure | One final full verify/build, then planner review | One final staging simulated-release browser flow |

Run a browser E2E only when at least one is true:

- a user-visible flow or authorization context materially changed;
- a new external simulator-process boundary reaches the browser;
- same-origin cookie/CSRF/redirect behavior is the risk;
- simulated completion and its non-financial label need browser evidence;
- an automated race result has a distinct UI recovery state worth observing.

Do not run E2E merely because a phase ended. Do not replay accepted document,
AI, video or casework matrices unless the current diff touches their boundary
or a targeted regression is necessary.

## 10. Closure protocol

Before moving any plan to `done/`:

- Every assigned and unassigned implementation phase is accepted.
- Every minimum invariant and required final validation is proven.
- Planner-owned browser acceptance passed where the plan requires it.
- External evidence is redacted and still reviewable.
- Material deviations and completion date are recorded.
- Applied migrations remain immutable accepted history.
- The complete plan Done checklist is checked honestly.
- The plan is moved to `done/`; `CURRENT.md` is updated only for material
  accepted-state change; the readiness charter is synchronized.

Task acceptance alone never completes a plan.

## 11. Stop and escalation rules

Stop and return `REPLAN` or `NO-GO` when:

- simulated mode or non-financial semantics are ambiguous;
- an unknown operation would require a new lifetime identity;
- behavior needs optimistic success, refund workaround or invented consent;
- a schema-v1 client would be silently broken without accepted migration/versioning;
- `updated_at`, browser time, callback-like input or AI output is proposed as the
  contractual release clock/authority;
- implementation enables the simulator in production, needs any provider
  credential/real money, or claims legal/KYC/custody/fee/split completion;
- module repository/entity sharing, external call inside transaction, secret
  exposure or accepted-migration edits are proposed;
- an ADR conflict or FORBIDDEN item appears.

Do not create a workaround to keep the sequence moving.

## 12. Maintenance rules

- Keep this document operational, concise and forward-looking; it may contain
  blocked/proposed state.
- Do not use `CURRENT.md` as a backlog or draft tracker.
- Do not rewrite historical acceptance records to reflect later work.
- Update phase/task mappings when an approved plan changes, but retain task IDs
  and revision history once issued.
- Link new acceptance evidence from the charter/runbook rather than embedding
  large logs or screenshots.
- Preserve user-owned worktrees and unrelated dirty files.
