# Repository Map (deterministic)

- Generated: 2026-07-27 10:16
- Commit: `596db75ad829f909163ee7c365c6a59f959834ff` (branch `main`)
- Root: `.` → `m4trust-spring-front-prod`

> FRESHNESS: this map is derived from the commit above. If `git rev-parse HEAD`
> differs, treat it as STALE and regenerate. The code is the source of truth;
> this map is a disposable index — never let it override what the code says.

## Languages
- `.java`: 460 files
- `.ts`: 50 files
- `.tsx`: 26 files
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

- 34× `frontend/src/@tanstack/react-query.ts`
- 19× `frontend/src/react.ts`
- 11× `frontend/src/react-router.ts`
- 1× `frontend/src/@vitejs/plugin-react.ts`
- 1× `frontend/src/react-dom/client.ts`
- 1× `frontend/src/vite.ts`

## Largest files (complexity hotspots)
- 7270 lines · `contracts/openapi/core-api-v1.yaml`
- 5760 lines · `frontend/src/generated/core-api.d.ts`
- 3786 lines · `frontend/src/styles.css`
- 2938 lines · `contracts/scripts/validate_contracts.py`
- 1612 lines · `services/core-api/src/test/java/com/m4trust/coreapi/casework/DisputeIntegrationTest.java`
- 1421 lines · `frontend/package-lock.json`
- 1390 lines · `architecture-decisions/ADR-006-Public-API-and-Error-Conventions.md`
- 1199 lines · `architecture-decisions/ADR-002-Spring-AI-Contract-and-Compatibility-Policy.md`
- 1185 lines · `frontend/src/features/fulfillment/DealFulfillmentPanel.tsx`
- 1177 lines · `architecture-decisions/ADR-003-Core-Domain-Model-and-Deal-Lifecycle.md`
- 1134 lines · `services/core-api/src/test/java/com/m4trust/coreapi/deal/DealIntegrationTest.java`
- 1112 lines · `architecture-decisions/ADR-007-Deployment-and-Runtime-Environments.md`

## Tests
- Test files found: 94
- Coverage signal for top modules:
  - `frontend/src/@tanstack/react-query.ts` — NO test signal
  - `frontend/src/react.ts` — NO test signal
  - `frontend/src/react-router.ts` — NO test signal
  - `frontend/src/@vitejs/plugin-react.ts` — NO test signal
  - `frontend/src/react-dom/client.ts` — test signal
  - `frontend/src/vite.ts` — NO test signal

## Top-level structure
```
.DS_Store
.claude/
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
  .venv/
  CHANGELOG.md
  README.md
  asyncapi/
  examples/
  openapi/
  requirements.txt
  schemas/
docs/
  .DS_Store
  DEVELOPMENT.md
  VALIDATION.md
  agent/
  plan/
  research/
frontend/
  .env
  .env.example
  .gitignore
  Caddyfile
  Dockerfile
  Dockerfile.dockerignore
  README.md
  dist/
infra/
  .env
  .env.example
  .gitignore
  README.md
  compose.yaml
scripts/
  dev-reset.ps1
  dev-seed.ps1
  dev-up.sh
  generate-repo-map.py
  validate-contracts.sh
  validate-frontend.sh
services/
  core-api/
tools/
  mock-ai-worker/
  moka-emulator/
```
