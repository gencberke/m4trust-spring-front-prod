# M4Trust Core API

Spring Boot modular-monolith Core API for M4Trust. The implemented foundation
includes PostgreSQL-backed identity and server-side session authentication,
tenant provisioning, legal entities, memberships, append-only business audit,
reusable application-layer legal-entity authorization, and the participant-
scoped Deal aggregate with optimistic concurrency and lifecycle projections.

## Operational endpoints

The reviewed `contracts/openapi/core-api-v1.yaml` defines these runtime
authentication and organization operations:

- `GET /api/v1/security/csrf` — issue/read the CSRF token and header name.
- `POST /api/v1/auth/register` — create an account and authenticated session.
- `POST /api/v1/auth/login` — authenticate and rotate the session identifier.
- `POST /api/v1/auth/logout` — invalidate the server-side session.
- `GET /api/v1/auth/me` — return safe public account fields and a required,
  non-null legal-entity membership array.
- `POST /api/v1/legal-entities` — create a legal entity and atomically assign
  the creator an `ADMIN` membership.
- `GET /api/v1/legal-entities` — list only the authenticated user's
  memberships.
- `GET /api/v1/legal-entities/{legalEntityId}` — return member-visible detail.
- `GET /api/v1/legal-entities/{legalEntityId}/members` — return the
  member-visible identity projection and role list.
- `POST /api/v1/deals` — create a `DRAFT` Deal for the active legal entity,
  add it as the initial participant, and append `DEAL_CREATED` atomically.
- `GET /api/v1/deals` — list only participant-visible Deals with stable
  pagination, optional status filtering, and allowlisted sorting.
- `GET /api/v1/deals/{dealId}` — return participant-visible detail with the
  backend-derived lifecycle and available actions.
- `PATCH /api/v1/deals/{dealId}` — replace editable basic fields using the
  required `expectedVersion`; stale writes and invalid states return distinct
  stable conflict codes.
- `POST /api/v1/deals/{dealId}/cancel` — apply the aggregate cancellation rule
  and return the current detail projection.

All state-changing operations require the CSRF token from
`GET /api/v1/security/csrf` in the response's declared header. The two scoped
legal-entity reads also require `X-M4Trust-Legal-Entity-Id` to match the path.
Every Deal operation requires the same header; the application layer first
verifies legal-entity membership and then enforces Deal participation. Active
selection is never stored in the session. Actuator endpoints remain operational
surfaces outside the public contract:

- `GET /actuator/health` — overall health.
- `GET /actuator/health/liveness` — liveness probe.
- `GET /actuator/health/readiness` — readiness probe.
- `GET /actuator/info` — build information when available.

Errors use RFC 9457 Problem Details with stable machine-readable codes and a
request correlation ID.

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
Spring Session runtime schema initialization is disabled so Flyway remains the
only schema owner. Migration files use `V<version>__<description>.sql` names,
are forward-only, and are never edited after application. Seed data never
belongs in this chain.

## Module boundaries

The modular monolith currently contains these explicit boundaries:

- `identity` — account registration, credential verification, and the safe
  public user projection; password hashes remain internal to this module.
- `organization` — tenants, legal entities, memberships, and the reusable
  `OperationContext` authorization boundary.
- `audit` — append-only business audit persistence through a narrow port that
  joins the caller's business transaction.
- `deal` — the Deal aggregate, centralized lifecycle behavior, participant-
  scoped JDBC persistence, public projections, and create/update/cancel
  application transactions.

- `sharedkernel` — genuinely shared, stable primitives; never generic helpers
  or module-specific business rules.
- `integration` — external adapters and reliable-delivery plumbing; never
  business decisions.

Future business modules are created only by the slice that needs them. A small
ArchUnit test slices production code by top-level package and rejects cyclic
dependencies. ArchUnit is test-only and framework-neutral, which keeps the
check maintainable without forbidding ADR-approved collaboration through
ports, stable IDs, domain events, or read-only projections. More specific rules
require an accepted module contract rather than being guessed upfront.

## Authentication validation

Authentication integration tests run against a real disposable PostgreSQL
instance through Testcontainers. They prove email normalization and uniqueness,
Argon2id password storage, safe response projection, session rotation and
invalidation, CSRF enforcement, cookie policy, generic credential failures,
and the absolute session deadline. Docker must be available to run `mvn verify`.

Login throttling is explicitly deferred from Slice 1 and remains a required
follow-up before public launch; generic failures prevent account enumeration but
do not replace rate limiting.

## Organization validation

Organization integration tests also use real PostgreSQL. They prove creator
`ADMIN` assignment, the two audit appends and rollback atomicity, stable list
and detail projections, non-null `/auth/me` memberships, centralized header
validation, and 404 non-disclosure for nonexistent or hidden legal entities.
The browser end-to-end flow remains a separate acceptance step.

## Deal validation

Deal integration tests use MockMvc with disposable PostgreSQL. They prove the
frozen create/list/detail/update/cancel JSON surface, deterministic pagination,
status filtering, explicit-null description replacement, version increments,
stale and state conflict codes, participant non-disclosure, legal-entity
context error semantics, non-null empty lists, all mutation audit actions, and
rollback when the mandatory audit append fails. The separate browser flow
remains the Slice 3 end-to-end acceptance step.

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
