# M4Trust Mock AI Worker

Local-only document-extraction worker for exercising the real RabbitMQ boundary.
It consumes `m4trust.ai.document-extraction.v1`, downloads the request's
short-lived object reference, verifies its byte count and SHA-256, and publishes
contract-valid completed or failed events to `m4trust.ai.events`.

The worker never interprets document content and never changes the production
event contract. It loads both schemas and payload templates directly from the
committed `contracts/` directory.

## Start locally

From the repository root:

```powershell
docker compose -f infra/compose.yaml --profile mock-ai up --build
```

Without `--profile mock-ai`, RabbitMQ and the core stack can run while the worker
remains off. The process also refuses to start unless
`M4TRUST_MOCK_AI_ENABLED=true`, and refuses `APP_ENVIRONMENT=production`.

## Deterministic scenarios

`M4TRUST_MOCK_AI_SCENARIO` may be set to `auto` (default), `success`,
`retryable_failure`, or `duplicate`. In `auto`, the downloaded reference's
request `fileName` selects the scenario without adding event fields:

- `fail-retryable*.pdf` -> a terminal `RETRYABLE_TECHNICAL` failure
- `duplicate*.pdf` -> the same completed event is published twice
- every other name -> a completed result containing an advisory `legalBasis`

Download and expected-object checks always run before scenario output. A hash
mismatch becomes the stable non-retryable `CONTENT_HASH_MISMATCH` failure;
exhausted transient download errors become
`OBJECT_STORAGE_TEMPORARILY_UNAVAILABLE`. Logs contain identifiers and stable
codes only, never URLs, request bodies, credentials, or document bytes.

The Compose profile connects to browser-reachable `localhost` presigned URLs
through Docker's `host.docker.internal` alias while preserving the signed Host
header. This is a local transport bridge only; event URLs are not rewritten.

## Validate

```powershell
python -m pip install -r tools/mock-ai-worker/requirements-dev.txt
$env:PYTHONPATH='tools/mock-ai-worker/src'
python -m pytest tools/mock-ai-worker/tests
python contracts/scripts/validate_contracts.py
docker compose -f infra/compose.yaml config
docker build -f tools/mock-ai-worker/Dockerfile -t m4trust-mock-ai-worker .
```

The optional broker smoke test expects RabbitMQ at the configured local host and
proves request -> HTTP download -> result publish:

```powershell
$env:PYTHONPATH='tools/mock-ai-worker/src'
python tools/mock-ai-worker/tests/smoke_rabbitmq.py
```
