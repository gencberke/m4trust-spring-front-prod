# Light validation map

Use package-local checks that match the surface you changed. Do not default to full
monorepo verify loops for small edits.

## Frontend (TypeScript)

| Goal | Command |
| --- | --- |
| TS-only, no OpenAPI regen | `cd frontend && npm run typecheck:fast` |
| Full typecheck (regenerates API types) | `cd frontend && npm run typecheck` |
| Lint | `cd frontend && npm run lint` |
| Unit tests | `cd frontend && npm run test` |
| Tests in watch mode | `cd frontend && npm run test:watch` |
| Formatting check | `cd frontend && npm run format:check` |
| Apply formatting | `cd frontend && npm run format` |
| Production build | `cd frontend && npm run build` |

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

`./mvnw verify` runs the full gate chain: unit and integration tests,
**Spotless** formatting check, and **JaCoCo** line coverage. The measured bundle must stay
at or above **85%** — this is an ADR-004 **regression floor**, not a delivery target or
slice-acceptance evidence. Primary acceptance remains critical invariants, contract checks,
architecture rules, and browser-visible flows. A formatting violation or coverage shortfall
fails the build the same way a failing test does.

Before `verify`, install the Python packages from
[`contracts/requirements.txt`](../contracts/requirements.txt) (for example in
`contracts/.venv`), or point `M4TRUST_PYTHON` at another interpreter that has
those dependencies. The application build workflow installs them for CI; local
runs must do the same or integration tests that spawn Python helpers will fail.

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
| Regenerate repository map | `python3 scripts/generate-repo-map.py` |

The repository-hygiene workflow (`.github/workflows/repository-hygiene.yml`) runs
both checks for pull requests and pushes to `main`. Map freshness means
regeneration produces no diff; the map does not embed HEAD, branch, or wall-clock
time. The checker stays offline and does not validate external URLs or heading
anchors.

## Whitespace

```bash
git diff --check
```

## Rules for agents

1. Pick the lightest check that exercises the code you touched.
2. Do not run `./mvnw verify` for isolated Java unit changes when `-Dtest=<Class>` suffices.
3. Do not run `npm run typecheck` (OpenAPI regen) when `typecheck:fast` is enough.
4. Do not invent repo-root lint/format/test harnesses that are not documented here.
