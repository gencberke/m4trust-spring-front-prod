# Light validation map

Use package-local checks that match the surface you changed. Do not default to full
monorepo verify loops for small edits.

## Frontend (TypeScript)

| Goal | Command |
| --- | --- |
| TS-only, no OpenAPI regen | `cd frontend && npm run typecheck:fast` |
| Full typecheck (regenerates API types) | `cd frontend && npm run typecheck` |
| Production build | `cd frontend && npm run build` |

Prefer `typecheck:fast` when you did not change contracts or generated types.

Optional wrapper from repo root: `./scripts/validate-frontend.sh` (fast) or
`./scripts/validate-frontend.sh --full`.

## Core API (Java)

| Goal | Command |
| --- | --- |
| Focused unit test | `cd services/core-api && ./mvnw --batch-mode -Dtest=<Class> test` |
| Full module verify (Docker / Testcontainers) | `cd services/core-api && ./mvnw verify` |

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

## Whitespace

```bash
git diff --check
```

## Rules for agents

1. Pick the lightest check that exercises the code you touched.
2. Do not run `./mvnw verify` for isolated Java unit changes when `-Dtest=<Class>` suffices.
3. Do not run `npm run typecheck` (OpenAPI regen) when `typecheck:fast` is enough.
4. Do not invent repo-root lint/format/test harnesses that are not documented here.
