# Repository Map (deterministic)

- Freshness: regenerate with `python3 scripts/generate-repo-map.py`;
  committed output is current only when that command produces no diff.
- Source set: Git-tracked files plus non-ignored untracked candidates
  (`git ls-files -co --exclude-standard`), excluding build caches and
  virtual environments.

## Languages
- `.java`: 558 files
- `.ts`: 78 files
- `.tsx`: 62 files
- `.py`: 22 files

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

- 27× `frontend/src/app/coreApi.ts`
- 23× `frontend/src/features/deals/index.ts`
- 21× `frontend/src/shared/index.ts`
- 12× `frontend/src/features/organization/index.ts`
- 11× `frontend/src/features/fulfillment/fulfillmentApi.ts`
- 9× `frontend/src/features/deals/dealApi.ts`

## Largest files (complexity hotspots)
- 7269 lines · `contracts/openapi/core-api-v1.yaml`
- 5759 lines · `frontend/src/generated/core-api.d.ts`
- 4537 lines · `frontend/package-lock.json`
- 2937 lines · `contracts/scripts/validate_contracts.py`
- 2138 lines · `services/core-api/src/test/java/com/m4trust/coreapi/casework/DisputeIntegrationTest.java`
- 1389 lines · `architecture-decisions/ADR-006-Public-API-and-Error-Conventions.md`
- 1330 lines · `services/core-api/src/main/java/com/m4trust/coreapi/fulfillment/api/FulfillmentService.java`
- 1280 lines · `services/core-api/src/test/java/com/m4trust/coreapi/deal/api/DealIntegrationTest.java`
- 1253 lines · `services/core-api/src/test/java/com/m4trust/coreapi/fulfillment/FulfillmentIntegrationTest.java`
- 1198 lines · `architecture-decisions/ADR-002-Spring-AI-Contract-and-Compatibility-Policy.md`
- 1194 lines · `services/core-api/src/test/java/com/m4trust/coreapi/fulfillment/VideoAnalysisHardeningIntegrationTest.java`
- 1176 lines · `architecture-decisions/ADR-003-Core-Domain-Model-and-Deal-Lifecycle.md`

## Tests
- Test files found: 121
- Coverage signal for top modules:
  - `frontend/src/app/coreApi.ts` — test signal
  - `frontend/src/features/deals/index.ts` — NO test signal
  - `frontend/src/shared/index.ts` — NO test signal
  - `frontend/src/features/organization/index.ts` — NO test signal
  - `frontend/src/features/fulfillment/fulfillmentApi.ts` — NO test signal
  - `frontend/src/features/deals/dealApi.ts` — NO test signal

## Top-level structure
```
.github/
  workflows/
.gitignore
AGENTS.md
README.md
architecture-decisions/
  ADR-001-System-Boundaries-and-Data-Ownership.md
  ADR-002-Spring-AI-Contract-and-Compatibility-Policy.md
  ADR-003-Core-Domain-Model-and-Deal-Lifecycle.md
  ADR-004-Vertical-Slice-Delivery-and-Acceptance-Testing.md
  ADR-005-Authentication-and-Security-Baseline.md
  ADR-006-Public-API-and-Error-Conventions.md
  ADR-007-Deployment-and-Runtime-Environments.md
  ADR-008-Cross-Tenant-Deal-Participation.md
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
  check-markdown-links.py
  dev-reset.ps1
  dev-reset.sh
  dev-seed.ps1
  dev-seed.sh
  dev-up.ps1
  dev-up.sh
  generate-repo-map.py
services/
  core-api/
tools/
  mock-ai-worker/
  moka-emulator/
```
