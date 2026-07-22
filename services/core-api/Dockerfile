# syntax=docker/dockerfile:1

# Build from the monorepo root:
#   docker build -f services/core-api/Dockerfile .

# ---- Build stage -----------------------------------------------------------
FROM eclipse-temurin:21-jdk AS build

WORKDIR /workspace

# Preserve the Maven module's ../../contracts relative layout.
COPY services/core-api/.mvn/ /workspace/services/core-api/.mvn/
COPY services/core-api/mvnw /workspace/services/core-api/mvnw
COPY services/core-api/pom.xml /workspace/services/core-api/pom.xml
COPY contracts/ /workspace/contracts/

WORKDIR /workspace/services/core-api
# Windows checkouts may leave CRLF on mvnw; strip CR so the Linux shebang works.
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY services/core-api/src/ /workspace/services/core-api/src/
RUN ./mvnw -B -q clean package -DskipTests

# ---- Runtime stage ----------------------------------------------------------
FROM eclipse-temurin:21-jre-jammy AS runtime

ARG APP_VERSION=unknown
ARG GIT_COMMIT_SHA=
ARG APP_ENVIRONMENT=unknown
ARG BUILD_TIME=unknown
ARG CONTRACT_BUNDLE_DIGEST=

LABEL org.opencontainers.image.title="m4trust-core-api" \
      org.opencontainers.image.version="${APP_VERSION}" \
      org.opencontainers.image.revision="${GIT_COMMIT_SHA}" \
      org.opencontainers.image.created="${BUILD_TIME}" \
      io.m4trust.image.environment="${APP_ENVIRONMENT}" \
      io.m4trust.contract-bundle-digest="${CONTRACT_BUNDLE_DIGEST}"

RUN groupadd --system m4trust && useradd --system --gid m4trust --no-create-home m4trust

WORKDIR /app
COPY --from=build /workspace/services/core-api/target/core-api-*.jar app.jar
COPY services/core-api/docker/m4trust-core-api /usr/local/bin/m4trust-core-api

RUN sed -i 's/\r$//' /usr/local/bin/m4trust-core-api \
    && chmod 0755 /usr/local/bin/m4trust-core-api \
    && chown -R m4trust:m4trust /app
USER m4trust

# Runtime port is provided by the environment (e.g. Railway); no port is
# baked into the image or the architecture. 8080 is only a local default.
ENV SERVER_PORT=8080 \
    APP_VERSION="${APP_VERSION}" \
    GIT_COMMIT_SHA="${GIT_COMMIT_SHA}" \
    APP_ENVIRONMENT="${APP_ENVIRONMENT}" \
    BUILD_TIME="${BUILD_TIME}" \
    CONTRACT_BUNDLE_DIGEST="${CONTRACT_BUNDLE_DIGEST}"
EXPOSE 8080

ENTRYPOINT ["m4trust-core-api"]
CMD ["run"]
