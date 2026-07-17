# syntax=docker/dockerfile:1

# ---- Build stage -----------------------------------------------------------
FROM eclipse-temurin:21-jdk AS build

WORKDIR /workspace

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY src/ src/
RUN ./mvnw -B -q clean package -DskipTests

# ---- Runtime stage ----------------------------------------------------------
FROM eclipse-temurin:21-jre-jammy AS runtime

ARG APP_VERSION=unknown
ARG GIT_COMMIT_SHA=unknown
ARG APP_ENVIRONMENT=unknown
ARG BUILD_TIME=unknown

LABEL org.opencontainers.image.title="m4trust-core-api" \
      org.opencontainers.image.version="${APP_VERSION}" \
      org.opencontainers.image.revision="${GIT_COMMIT_SHA}" \
      org.opencontainers.image.created="${BUILD_TIME}" \
      io.m4trust.image.environment="${APP_ENVIRONMENT}"

RUN groupadd --system m4trust && useradd --system --gid m4trust --no-create-home m4trust

WORKDIR /app
COPY --from=build /workspace/target/core-api-*.jar app.jar
COPY docker/m4trust-core-api /usr/local/bin/m4trust-core-api

RUN chmod 0755 /usr/local/bin/m4trust-core-api \
    && chown -R m4trust:m4trust /app
USER m4trust

# Runtime port is provided by the environment (e.g. Railway); no port is
# baked into the image or the architecture. 8080 is only a local default.
ENV SERVER_PORT=8080 \
    APP_VERSION="${APP_VERSION}" \
    GIT_COMMIT_SHA="${GIT_COMMIT_SHA}" \
    APP_ENVIRONMENT="${APP_ENVIRONMENT}" \
    BUILD_TIME="${BUILD_TIME}"
EXPOSE 8080

ENTRYPOINT ["m4trust-core-api"]
CMD ["run"]
