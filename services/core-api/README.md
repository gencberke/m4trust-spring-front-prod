# M4Trust Core API

Spring Boot platform foundation skeleton for the M4Trust Core API. This is a
Slice 0 increment: it proves the public API, health, and validation
conventions are wired correctly. It contains no business capability.

## Endpoints

- `GET /api/v1/meta` — release identity (`buildVersion`, `gitCommitSha`,
  `environment`, `buildTime`).
- `POST /api/v1/echo` — validation demo. Body: `{"message": "..."}`.
  Invalid input (blank `message`) returns `422` as
  `application/problem+json` with an `errors` array. Malformed JSON returns
  `400`.
- `GET /actuator/health` — overall health.
- `GET /actuator/health/liveness` — liveness probe.
- `GET /actuator/health/readiness` — readiness probe.

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
- `APP_ENVIRONMENT` — environment label reported by `/api/v1/meta`; falls
  back to the active Spring profile, then `"local"`.
- `GIT_COMMIT_SHA` — commit SHA reported by `/api/v1/meta`, intended to be
  set by CI/CD at build or deploy time; falls back to `"unknown"`.
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

## Readiness note

`/actuator/health/readiness` includes both `readinessState` and PostgreSQL's
`db` health indicator, so the service does not accept traffic without a
database connection. `/actuator/health/liveness` includes only
`livenessState`; a database outage does not trigger process restarts.
