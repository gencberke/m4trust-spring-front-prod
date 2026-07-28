# Light validation map

Use package-local checks that match the surface you changed. Do not default to full
monorepo verify loops for small edits.

## Frontend (TypeScript)

| Goal | Command |
| --- | --- |
| TS-only, no OpenAPI regen | `cd frontend && npm run typecheck:fast` |
| Generated API drift, no rewrite | `cd frontend && npm run generate:api:check` |
| Full typecheck (regenerates API types) | `cd frontend && npm run typecheck` |
| Lint | `cd frontend && npm run lint` |
| Unit tests | `cd frontend && npm run test` |
| Tests in watch mode | `cd frontend && npm run test:watch` |
| Formatting check | `cd frontend && npm run format:check` |
| Apply formatting | `cd frontend && npm run format` |
| Production build, no generated rewrite | `cd frontend && npm run build:check` |

Prefer `typecheck:fast` when you did not change contracts or generated types.

Lint must stay at zero errors. Warnings are tolerated only where they are
recorded as known debt in [`ROADMAP.md`](ROADMAP.md); do not silence a rule to
make a warning disappear.

Optional wrapper from repo root: `./scripts/validate-frontend.sh` (fast) or
`./scripts/validate-frontend.sh --full`.

## Core API (Java)

| Goal | Command |
| --- | --- |
| Focused unit test | `cd services/core-api && ./mvnw --batch-mode -Dtest=<Class> test` |
| Format check only (Spotless) | `cd services/core-api && ./mvnw --batch-mode spotless:check` |
| Apply Java formatting (Spotless) | `cd services/core-api && ./mvnw --batch-mode spotless:apply` |
| Full module verify (Docker / Testcontainers) | `cd services/core-api && ./mvnw verify` |

`./mvnw verify` runs the default backend gate chain: focused unit and integration
tests plus **Spotless** formatting. JaCoCo reporting is optional diagnostic work
under the explicit `coverage` profile; it has no percentage gate. Primary
acceptance remains critical invariants, contract checks, architecture rules and
public-boundary behavior. A formatting violation or failing retained proof fails
the build.

The Python contract validator is a separate contract gate. Install its packages
only when running that validator; Core verification does not shell out to it.

Run `./mvnw verify` only when integration coverage or migrations matter. Many
tests use Testcontainers and require a running Docker daemon.

Replace `<Class>` with a simple class name (for example `SettlementEligibilityEvaluatorTest`)
or a fully qualified name.

## Contracts (Python)

From the repository root on macOS or Linux (PEP 668–safe venv):

```bash
python3 -m venv contracts/.venv
source contracts/.venv/bin/activate
pip install -r contracts/requirements.txt
python contracts/scripts/validate_contracts.py
```

Optional wrapper: `./scripts/validate-contracts.sh`

See [contracts/README.md](../contracts/README.md) for Windows notes and validator details.

## Repository hygiene

| Goal | Command |
| --- | --- |
| Markdown reference check | `python3 scripts/check-markdown-links.py` |
| Architecture authority references | `python3 scripts/check-architecture-references.py` |
| Flyway history and checksums | `python3 scripts/check-flyway-history.py` |
| Check repository-map freshness | `python3 scripts/generate-repo-map.py --check` |
| Regenerate repository map | `python3 scripts/generate-repo-map.py` |

The repository-hygiene workflow (`.github/workflows/repository-hygiene.yml`) runs
these checks for pull requests and pushes to `main`. CI also compares Flyway
files with the base revision. Map freshness is checked without rewriting the
tracked map; the map does not embed HEAD, branch, or wall-clock time. The
Markdown checker stays offline and does not validate external URLs or heading
anchors.

## Complete repository validation

Use `./scripts/verify-repo.sh` on macOS/Linux or
`.\scripts\verify-repo.ps1` on Windows. The wrappers run contracts, Core,
frontend, local tools and hygiene without rewriting generated tracked files.
They print subsystem and total elapsed time but do not fail solely on duration.

The environment is expected to be dependency-ready. `M4TRUST_PYTHON` may point
at a Python environment containing contract-validator dependencies;
`M4TRUST_TOOLS_PYTHON` may independently point at one containing the mock-worker
test dependencies. `M4TRUST_BASE_SHA` may override the default `origin/main`
merge-base used for OpenAPI compatibility. Container image and deployment smoke
validation remains a separate release-artifact lane.

## Whitespace

```bash
git diff --check
```

## Rules for agents

1. Pick the lightest check that exercises the code you touched.
2. Do not run `./mvnw verify` for isolated Java unit changes when `-Dtest=<Class>` suffices.
3. Do not run `npm run typecheck` (OpenAPI regen) when `typecheck:fast` is enough.
4. Use the documented repository validation wrappers for cross-repository work;
   they report timing but do not fail solely on wall-clock duration.
