# Review Request
Task: PHOTO-ANALYSIS-INPUT
Revision: 1
Plan: ad-hoc AI-worker photo evidence analysis handoff (Downloads/berke-son-prompt.txt)
Phases: ADR + V24 + Java gate + frontend gate + OpenAPI text + focused tests
Status: COMPLETED
Branch: feat/video-analysis-photo-input
Base: main@693add7
Plan completion claim: NO

## Phase outcomes
- ADR — DONE — ADR-012 §2.1/gate #2 + 2026-07-23 amendment; ADR-011 §2.7 subject note; INDEX/README synced
- V24 — DONE — CHECK widened to video/mp4|image/jpeg|image/png
- Java gate — DONE — `isSupportedAnalysisInput` strict VIDEO+MP4 / PHOTO+JPEG|PNG
- Frontend gate — DONE — `isAnalysisEligibleEvidence` mirrors backend
- OpenAPI/CHANGELOG — DONE — description-only eligibility text; generated types regenerated
- Tests — DONE — migration jpeg/png accept + pdf reject; PHOTO+jpeg request happy path

## Validation
- `VideoAnalysisMigrationIntegrationTest,VideoAnalysisRequestIntegrationTest` — PASS
- `frontend npm run typecheck` — PASS
- `git diff --check` — PASS

## Decisions needed
- None for this branch. Plan 18 coordination notes only:
  - Plan 18B also needs a forward migration after V23 and an ADR-011 amendment (evidencePolicy). This branch took V24 for photo CHECK; Plan 18 must use V25+.
  - ADR-011 edits are different sections (§2.7 subject vs evidencePolicy) but same-file merge risk.
  - `DealFulfillmentPanel` is shared with Plan 18B/C (localized analysis gate vs policy/cancel UX).

## Deviation or risk
- Strict VIDEO+mp4 / PHOTO+jpeg|png pairing (safer than loose OR of all evidenceType×mediaType combinations from the handoff note).
