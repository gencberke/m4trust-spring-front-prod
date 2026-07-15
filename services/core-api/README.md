# M4Trust Core API

Spring Boot platform foundation skeleton for the M4Trust Core API. Slice 0 has
no public application endpoint or business capability; it establishes the
operational health, Problem Details, correlation, migration, and module
foundations.

## Operational endpoints

The production application still has no public endpoint. The reviewed
`contracts/openapi/core-api-v1.yaml` now defines the planned Slice 1
authentication surface before implementation; those operations are not yet
available at runtime. The endpoints below are Spring Boot Actuator operational
surfaces and are not part of the public contract:

- `GET /actuator/health` — overall health.
- `GET /actuator/health/liveness` — liveness probe.
- `GET /actuator/health/readiness` — readiness probe.
- `GET /actuator/info` — build information when available.

Problem Details validation and correlation behavior are exercised through a
test-only MVC probe that is never packaged as a production endpoint.

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

## Configuration

Non-secret configuration is environment-variable driven:

- `SERVER_PORT` / `PORT` — runtime HTTP port (default `8080` locally).
- `SPRING_PROFILES_ACTIVE` — active Spring profile.
- `APP_ENVIRONMENT` — environment label included in structured logs; falls
  back to the active Spring profile, then `"local"`.
- `APP_VERSION` — release version included in structured logs; defaults to
  `"unknown"` when no runtime release identity is supplied.
- `DATABASE_HOST` — PostgreSQL host.
- `DATABASE_PORT` — PostgreSQL port.
- `DATABASE_NAME` — PostgreSQL database name.
- `DATABASE_USER` — PostgreSQL user.
- `DATABASE_PASSWORD` — PostgreSQL password; supply it through environment
  secret management and never commit it.

All five database variables are required outside the explicit `local` Spring
profile, so a deployment with missing database configuration fails during
startup. The `local` profile defaults to `127.0.0.1:5432`, database and user
`m4trust_local`, and the clearly local placeholder password used by Compose.

## Database migrations

Flyway runs the versioned migrations in `src/main/resources/db/migration`
against PostgreSQL during local startup. Migration files use
`V<version>__<description>.sql` names, for example `V1__baseline.sql`.
Migrations are forward-only: once applied, a file is immutable and every
change is a new versioned migration. Seed data never belongs in this chain.

## Module boundaries

The modular-monolith foundation currently contains only two module skeletons:

- `sharedkernel` — genuinely shared, stable primitives; never generic helpers
  or module-specific business rules.
- `integration` — external adapters and reliable-delivery plumbing; never
  business decisions.

Future business modules are created only by the slice that needs them. A
small ArchUnit test slices production code by top-level package and rejects
cyclic dependencies. ArchUnit is test-only and framework-neutral, which keeps
the check maintainable without forbidding ADR-approved collaboration through
ports, stable IDs, domain events, or read-only projections. More specific
rules require an accepted module contract rather than being guessed upfront.

## Structured logging

Spring Boot's built-in Logstash formatter writes JSON to the console. Each
record has `timestamp`, `level`, `service`, `environment`, `version`, and
`message`. During request handling, `CorrelationIdFilter` puts
`correlationId` in the MDC and Boot adds it to the same JSON record. No file
appender is configured. Credentials, tokens, raw business content, and
unnecessary personal data must never be logged.

## Readiness note

`/actuator/health/readiness` includes both `readinessState` and PostgreSQL's
`db` health indicator, so the service does not accept traffic without a
database connection. `/actuator/health/liveness` includes only
`livenessState`; a database outage does not trigger process restarts.
