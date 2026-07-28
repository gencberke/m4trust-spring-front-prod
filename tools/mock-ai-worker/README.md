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

## Critical local boundary

The retained local proof is deliberately narrow: an enabled local worker
downloads one document reference, validates the request and emits one
contract-valid correlated result. A broker message is acknowledged only after a
persistent confirmed publish; an invalid request is dead-lettered without a
result publish. Logs contain identifiers and stable codes only, never URLs,
request bodies, credentials, or document bytes.

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
