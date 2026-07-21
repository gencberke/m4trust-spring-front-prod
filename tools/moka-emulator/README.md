# M4Trust Moka HTTP Emulator

This is a standalone local/CI-only HTTP transport test double for Slice 11B-A.
It is neither Spring nor a business-database client, and its output is not
Moka-provider evidence, production semantics, pool finality, or G1 evidence.

## Start

```sh
M4TRUST_MOKA_EMULATOR_ENABLED=true \
M4TRUST_MOKA_EMULATOR_SCENARIOS=success,decline,timeout_then_late_success \
PYTHONPATH=tools/moka-emulator/src \
python -m m4trust_moka_emulator
```

The process refuses startup without explicit enablement and when
`APP_ENVIRONMENT` is `staging` or `production`. Scenario ordering is fixed at
process startup and is consumed once per new `OtherTrxCode`; no HTTP header,
body, amount, currency, Core API field, or runtime endpoint selects it. State
is intentionally in-memory only, so restart starts the configured sequence
again and loses every request identity.

## Bounded routes

- `GET /health`
- `POST /PaymentDealer/DoDirectPayment` — funding initiate
- `POST /PaymentDealer/GetDealerPaymentTrxDetailList` — funding/query probe
- `POST /PaymentDealer/DoApprovePoolPayment` — probe-only pool approval

Every POST requires a JSON object with a bounded `OtherTrxCode`. Requests are
limited to 8192 bytes. The emulator retains no secrets and emits only synthetic
references/codes; it does not log request or response payloads.

Supported startup scenarios are `success`, `decline`,
`timeout_then_late_success`, `malformed_error`, and `not_found`. A repeated
initiate with the same `OtherTrxCode` returns the same emulator identity and
does not consume a scenario or create a second record. The timeout scenario
records identity then closes the response; its first query is `UNCONFIRMED`
and its later query is `SUCCESS`. This is test-double behavior only.

The documented transport and safety matrix is
[`fixtures/transport-matrix.json`](fixtures/transport-matrix.json). It marks
direct-charge duplicate behavior, pool/finality behavior, and other research
gaps as `UNKNOWN`.

## Validate

```sh
python3 -m unittest discover -s tools/moka-emulator/tests -p 'test_*.py' -v
docker build -f tools/moka-emulator/Dockerfile -t m4trust-moka-emulator .
```
