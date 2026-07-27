# M4Trust Core API

Spring Boot modular-monolith Core API for M4Trust. The service implements the
full deal lifecycle (identity, organization, deals, documents, contract
intelligence, review, ratification, funding, fulfillment, disputes/casework, and
simulated settlement) behind the reviewed OpenAPI contract.

Public HTTP operations, schemas, and error codes are defined in
[`contracts/openapi/core-api-v1.yaml`](../../contracts/openapi/core-api-v1.yaml).
See [`contracts/README.md`](../../contracts/README.md) for contract workflow and
[`docs/VALIDATION.md`](../../docs/VALIDATION.md) for build and test commands.

Actuator health endpoints (`/actuator/health/*`) are operational surfaces outside
the public contract. Errors use RFC 9457 Problem Details with stable machine-
readable codes and a request correlation ID.

## Source layout

Java sources live under `src/main/java/com/m4trust/coreapi/`. Each business
boundary is a top-level package with `api`, `domain`, and `infra` subpackages
where applicable:

| Package | Role |
| --- | --- |
| `api` | Cross-cutting HTTP infrastructure (filters, Problem Details, shared error codes) |
| `audit` | Append-only business audit port and JDBC adapter |
| `casework` | Dispute cases and casework workflows |
| `contractintelligence` | Document extraction and analysis orchestration |
| `contracts` | Contract-bundle helpers used by the API surface |
| `deal` | Deal aggregate, invitations, parties, lifecycle projections |
| `deployment` | `run` / `migrate` process entrypoints |
| `document` | Deal document upload and storage orchestration |
| `fulfillment` | Evidence submission and video-analysis handoff |
| `idempotency` | HTTP idempotency persistence |
| `identity` | Registration, sessions, CSRF, credential handling |
| `integration` | Messaging, object storage, and external provider adapters |
| `organization` | Tenants, legal entities, memberships, operation context |
| `payment` | Funding plans and simulated settlement |
| `ratification` | Ratification packages and confirmations |
| `sharedkernel` | Reserved shared primitives scaffold (currently minimal) |

`ModuleArchitectureTest` enforces acyclic module dependencies and the
`api` / `domain` / `infra` layering rules.

## Run locally

Requires Java 21 and the local PostgreSQL service. The project uses the Maven
Wrapper, so no local Maven install is required. The explicit `local` profile
uses the placeholder database settings from `infra/compose.yaml`.

```bash
SPRING_PROFILES_ACTIVE=local ./mvnw spring-boot:run
```

PowerShell:

```powershell
$env:SPRING_PROFILES_ACTIVE = "local"
.\mvnw.cmd spring-boot:run
```

The app listens on `SERVER_PORT` (or `PORT`), defaulting to `8080` locally.

Build a jar and run it directly:

```bash
./mvnw clean package
SPRING_PROFILES_ACTIVE=local java -jar target/core-api-*.jar
```

## Run with Docker

```bash
docker build -t m4trust-core-api .
docker run --rm -p 8080:8080 -e SERVER_PORT=8080 \
  -e DATABASE_HOST -e DATABASE_PORT -e DATABASE_NAME \
  -e DATABASE_USER -e DATABASE_PASSWORD m4trust-core-api
```

The container runs as a non-root user and reads its runtime port from the
`SERVER_PORT` environment variable — no port is hard-coded in the image.

The same immutable image exposes two explicit process modes:

```bash
m4trust-core-api run
m4trust-core-api migrate
```

`run` starts the web process with the configured Flyway policy. `migrate`
starts a minimal non-web Spring context, forces Flyway on, applies the migration
chain once, closes the context, and exits. It returns zero only when startup and
migration succeed; an uncaught failure produces a non-zero process exit.

## Configuration

Non-secret configuration is environment-variable driven:

- `SERVER_PORT` / `PORT` — runtime HTTP port (default `8080` locally).
- `SPRING_PROFILES_ACTIVE` — active Spring profile.
- `APP_ENVIRONMENT` — environment label included in structured logs; falls
  back to the active Spring profile, then `"local"`.
- `APP_VERSION` — release version included in structured logs; defaults to
  `"unknown"` when no runtime release identity is supplied.
- `GIT_COMMIT_SHA` — full immutable source revision included in structured logs
  and `/actuator/info`.
- `BUILD_TIME` — RFC 3339 build timestamp included in structured logs and
  `/actuator/info`.
- `DATABASE_HOST` — PostgreSQL host.
- `DATABASE_PORT` — PostgreSQL port.
- `DATABASE_NAME` — PostgreSQL database name.
- `DATABASE_USER` — PostgreSQL user.
- `DATABASE_PASSWORD` — PostgreSQL password; supply it through environment
  secret management and never commit it.
- `SESSION_IDLE_TIMEOUT` — inactivity timeout for server-side sessions
  (default `30m`).
- `SESSION_ABSOLUTE_TIMEOUT` — maximum session lifetime regardless of activity
  (default `8h`).
- `SESSION_COOKIE_NAME` — session cookie name (default
  `__Host-M4TRUST_SESSION`; local profile default `M4TRUST_SESSION`).
- `SESSION_COOKIE_SECURE` — require HTTPS transport for the session cookie
  (default `true`; local profile default `false`).

All five database variables are required outside the explicit `local` Spring
profile, so a deployment with missing database configuration fails during
startup. The `local` profile defaults to `127.0.0.1:5432`, database and user
`m4trust_local`, and the clearly local placeholder password used by Compose.

## Database migrations

Flyway runs the versioned migrations in `src/main/resources/db/migration`.
Local development enables startup migration; deployed runtime defaults keep it
off and invoke `m4trust-core-api migrate` as the single pre-deploy owner.
`V2__identity_user.sql` owns the normalized,
uniquely indexed identity account table, `V3__spring_session_jdbc.sql` owns the
Spring Session JDBC tables and indexes, and
`V4__organization_and_audit_foundation.sql` owns tenants, legal entities,
memberships, and append-only audit storage.
`V5__deal_foundation.sql` owns Deal state, the generated human-readable
reference sequence, optimistic-lock version, and participant access relation.
`V6` and `V7` expand and switch participant storage to the accepted
cross-tenant visibility model, `V8` owns reusable HTTP idempotency, `V9` owns
Deal invitations, and `V10` enforces buyer/seller references to the same Deal's
participant rows plus the database-level buyer-not-equal-seller invariant.
Spring Session runtime schema initialization is disabled so Flyway remains the
only schema owner. Migration files use `V<version>__<description>.sql` names,
are forward-only, and are never edited after application. Seed data never
belongs in this chain.

## Module boundaries

The modular monolith uses explicit package boundaries (see table above). Future
business modules are added only by the slice that needs them. `ModuleArchitectureTest`
slices production code by top-level package, rejects cyclic dependencies, and
enforces `api` / `domain` / `infra` layering. ArchUnit is test-only.

For validation commands (focused tests, Spotless, JaCoCo, full `verify`), see
[`docs/VALIDATION.md`](../../docs/VALIDATION.md).

## Structured logging

Spring Boot's built-in Logstash formatter writes JSON to the console. Each
record has `timestamp`, `level`, `service`, `environment`, `version`,
`gitCommitSha`, `buildTime`, and `message`. During request handling,
`CorrelationIdFilter` puts
`correlationId` in the MDC and Boot adds it to the same JSON record. No file
appender is configured. Credentials, tokens, raw business content, and
unnecessary personal data must never be logged.

## Readiness note

`/actuator/health/readiness` includes both `readinessState` and PostgreSQL's
`db` health indicator, so the service does not accept traffic without a
database connection. `/actuator/health/liveness` includes only
`livenessState`; a database outage does not trigger process restarts.
