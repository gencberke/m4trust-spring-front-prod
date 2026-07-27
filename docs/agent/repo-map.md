# Repository Map (deterministic)

- Generated: 2026-07-27 15:17
- Commit: `6ff30929e1d2ffe5f034ab6453b54d71a3e77591` (branch `chore/repo-cleanup-phase-0`)
- Root: `.` → `m4trust-spring-front-prod`

> FRESHNESS: this map is derived from the commit above. If `git rev-parse HEAD`
> differs, treat it as STALE and regenerate. The code is the source of truth;
> this map is a disposable index — never let it override what the code says.

## Languages
- `.java`: 541 files
- `.ts`: 78 files
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
- 21× `frontend/src/shared/index.ts`
- 19× `frontend/src/features/deals/dealApi.ts`
- 10× `frontend/src/features/organization/organizationApi.ts`
- 9× `frontend/src/features/deals/dealQueries.ts`
- 9× `frontend/src/features/fulfillment/fulfillmentApi.ts`

## Largest files (complexity hotspots)
- 7270 lines · `contracts/openapi/core-api-v1.yaml`
- 5760 lines · `frontend/src/generated/core-api.d.ts`
- 4538 lines · `frontend/package-lock.json`
- 2938 lines · `contracts/scripts/validate_contracts.py`
- 2139 lines · `services/core-api/src/test/java/com/m4trust/coreapi/casework/DisputeIntegrationTest.java`
- 1390 lines · `architecture-decisions/ADR-006-Public-API-and-Error-Conventions.md`
- 1331 lines · `services/core-api/src/main/java/com/m4trust/coreapi/fulfillment/api/FulfillmentService.java`
- 1281 lines · `services/core-api/src/test/java/com/m4trust/coreapi/deal/api/DealIntegrationTest.java`
- 1254 lines · `services/core-api/src/test/java/com/m4trust/coreapi/fulfillment/FulfillmentIntegrationTest.java`
- 1200 lines · `services/core-api/src/test/java/com/m4trust/coreapi/fulfillment/VideoAnalysisHardeningIntegrationTest.java`
- 1199 lines · `architecture-decisions/ADR-002-Spring-AI-Contract-and-Compatibility-Policy.md`
- 1177 lines · `architecture-decisions/ADR-003-Core-Domain-Model-and-Deal-Lifecycle.md`

## Tests
- Test files found: 100
- Coverage signal for top modules:
  - `frontend/src/app/coreApi.ts` — test signal
  - `frontend/src/shared/index.ts` — NO test signal
  - `frontend/src/features/deals/dealApi.ts` — NO test signal
  - `frontend/src/features/organization/organizationApi.ts` — NO test signal
  - `frontend/src/features/deals/dealQueries.ts` — NO test signal
  - `frontend/src/features/fulfillment/fulfillmentApi.ts` — NO test signal

## Top-level structure
```
.DS_Store
.claude/
AGENTS.md
HANDOFF.md
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
  .DS_Store
  DEVELOPMENT.md
  ROADMAP.md
  VALIDATION.md
  agent/
  history/
  plan/
  research/
frontend/
  .env
  .env.example
  .gitignore
  .prettierignore
  .prettierrc.json
  Caddyfile
  Dockerfile
  Dockerfile.dockerignore
infra/
  .env
  .env.example
  .gitignore
  README.md
  compose.yaml
scripts/
  dev-reset.ps1
  dev-reset.sh
  dev-seed.ps1
  dev-seed.sh
  dev-up.ps1
  dev-up.sh
  generate-repo-map.py
  validate-contracts.sh
services/
  core-api/
tools/
  mock-ai-worker/
  moka-emulator/
```
