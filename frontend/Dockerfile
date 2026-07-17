# syntax=docker/dockerfile:1

ARG APP_VERSION=unknown
ARG GIT_COMMIT_SHA=unknown
ARG APP_ENVIRONMENT=unknown
ARG BUILD_TIME=unknown

# The build context is the repository root because API type generation reads
# contracts/openapi/core-api-v1.yaml outside the frontend directory.
FROM node:22-alpine AS build

WORKDIR /workspace/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci

COPY contracts/openapi/core-api-v1.yaml /workspace/contracts/openapi/core-api-v1.yaml
COPY frontend/ ./
RUN npm run build

FROM caddy:2.10-alpine AS runtime

ARG APP_VERSION
ARG GIT_COMMIT_SHA
ARG APP_ENVIRONMENT
ARG BUILD_TIME

LABEL org.opencontainers.image.title="m4trust-web-edge" \
      org.opencontainers.image.version="${APP_VERSION}" \
      org.opencontainers.image.revision="${GIT_COMMIT_SHA}" \
      org.opencontainers.image.created="${BUILD_TIME}" \
      io.m4trust.image.environment="${APP_ENVIRONMENT}"

RUN addgroup -S caddy-runtime \
    && adduser -S -D -H -G caddy-runtime caddy-runtime

COPY frontend/Caddyfile /etc/caddy/Caddyfile
COPY --from=build --chown=caddy-runtime:caddy-runtime /workspace/frontend/dist/ /srv/

# Railway supplies PORT at runtime. 8080 is only a local container default.
ENV PORT=8080
EXPOSE 8080

USER caddy-runtime
CMD ["caddy", "run", "--config", "/etc/caddy/Caddyfile"]
