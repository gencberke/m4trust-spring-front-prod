# Repository Map (deterministic)

- Freshness: regenerate with `python3 scripts/generate-repo-map.py`;
  committed output is current only when that command produces no diff.
- Source set: Git-tracked files plus non-ignored untracked candidates
  (`git ls-files -co --exclude-standard`), excluding build caches and
  virtual environments.

## Languages
- `.java`: 494 files
- `.ts`: 77 files
- `.tsx`: 56 files
- `.py`: 21 files

## Build / tooling
- Python: `tools/mock-ai-worker/requirements.txt`
- Docker: `tools/mock-ai-worker/Dockerfile`
- Docker: `tools/moka-emulator/Dockerfile`
- Docker: `frontend/Dockerfile`
- Node/JS: `frontend/package.json`
- Python: `contracts/requirements.txt`
- Docker: `services/core-api/Dockerfile`
- Java/Maven: `services/core-api/pom.xml`

## Entry points (heuristic)
- `frontend/src/main.tsx`
- `tools/mock-ai-worker/src/m4trust_mock_worker/__main__.py`
- `tools/moka-emulator/src/m4trust_moka_emulator/__main__.py`
- `tools/moka-emulator/src/m4trust_moka_emulator/server.py`

## Load-bearing modules — by import fan-in
_How many internal files import each module. High fan-in = high blast radius =
prime test candidate (feeds test-driven-development)._

- 26× `frontend/src/app/coreApi.ts`
- 23× `frontend/src/features/deals/index.ts`
- 21× `frontend/src/shared/index.ts`
- 14× `frontend/src/features/organization/index.ts`
- 10× `frontend/src/features/fulfillment/fulfillmentApi.ts`
- 9× `frontend/src/features/auth/authApi.ts`

## Largest files (complexity hotspots)
- 7266 lines · `contracts/openapi/core-api-v1.yaml`
- 5759 lines · `frontend/src/generated/core-api.d.ts`
- 4366 lines · `frontend/package-lock.json`
- 2937 lines · `contracts/scripts/validate_contracts.py`
- 1330 lines · `services/core-api/src/main/java/com/m4trust/coreapi/fulfillment/api/FulfillmentService.java`
- 998 lines · `services/core-api/src/test/java/com/m4trust/coreapi/casework/DisputeIntegrationTest.java`
- 948 lines · `services/core-api/src/main/java/com/m4trust/coreapi/casework/api/DisputeService.java`
- 849 lines · `services/core-api/src/test/java/com/m4trust/coreapi/fulfillment/FulfillmentIntegrationTest.java`
- 819 lines · `services/core-api/src/test/java/com/m4trust/coreapi/payment/infra/PaymentFundingIntegrationTest.java`
- 759 lines · `docs/history/hackathon-2026-07/done/13-video-analysis.md`
- 759 lines · `frontend/src/shared/styles/base.css`
- 756 lines · `docs/history/hackathon-2026-07/done/14a-dispute-and-casework-foundation.md`

## Tests
- Test files found: 48
- Coverage signal for top modules:
  - `frontend/src/app/coreApi.ts` — test signal
  - `frontend/src/features/deals/index.ts` — NO test signal
  - `frontend/src/shared/index.ts` — NO test signal
  - `frontend/src/features/organization/index.ts` — NO test signal
  - `frontend/src/features/fulfillment/fulfillmentApi.ts` — NO test signal
  - `frontend/src/features/auth/authApi.ts` — NO test signal

## Top-level structure
```
.github/
  workflows/
.gitignore
AGENTS.md
README.md
architecture-decisions/
  ADR-AI-INTEGRATION.md
  ADR-BACKEND.md
  ADR-DEPLOYMENT.md
  ADR-ENGINEERING.md
  ADR-FRONTEND.md
  ADR-INDEX.md
  README.md
contracts/
  CHANGELOG.md
  README.md
  asyncapi/
  examples/
  openapi/
  requirements.txt
  schemas/
  scripts/
docs/
  DEVELOPMENT.md
  ROADMAP.md
  VALIDATION.md
  agent/
  history/
  plan/
  research/
frontend/
  .env.example
  .gitignore
  .prettierignore
  .prettierrc.json
  Caddyfile
  Dockerfile
  Dockerfile.dockerignore
  README.md
infra/
  .env.example
  .gitignore
  README.md
  compose.yaml
scripts/
  check-architecture-references.py
  check-flyway-history.py
  check-markdown-links.py
  dev-reset.ps1
  dev-reset.sh
  dev-seed.ps1
  dev-seed.sh
  dev-up.ps1
services/
  core-api/
tools/
  mock-ai-worker/
  moka-emulator/
```
