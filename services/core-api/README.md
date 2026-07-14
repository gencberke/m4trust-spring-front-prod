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

Requires Java 21. The project uses the Maven Wrapper, so no local Maven
install is required.

```bash
./mvnw spring-boot:run
```

The app listens on `SERVER_PORT` (or `PORT`), defaulting to `8080` locally.

Build a jar and run it directly:

```bash
./mvnw clean package
java -jar target/core-api-*.jar
```

## Run with Docker

```bash
docker build -t m4trust-core-api .
docker run --rm -p 8080:8080 -e SERVER_PORT=8080 m4trust-core-api
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

## Readiness note

Readiness currently reflects only the process itself: there is no database
or other external dependency in this increment. Once PostgreSQL is
introduced, readiness must be extended to reflect that dependency, per the
deployment ADR. Until then, `/actuator/health/readiness` intentionally does
not depend on anything external.
