# M4Trust frontend

Vite + React + TypeScript client for the same-origin M4Trust Core API. The
application covers the full deal workspace (organization bootstrap, deals,
documents, analysis, review, ratification, funding, fulfillment, settlement,
disputes, and invitations) using real Spring session endpoints through React
Router and TanStack Query. There is no mock user or browser-stored
authentication state.

Public request/response shapes come from the committed OpenAPI contract via
`src/generated/core-api.d.ts`. See [`contracts/README.md`](../contracts/README.md)
for the contract source of truth and [`docs/VALIDATION.md`](../docs/VALIDATION.md)
for lint, test, and build commands.

## Source layout

```
src/
  app/           Core API client, session helpers, route guards
  pages/         Route-level composition only (no feature mutations)
  features/      Feature modules (each with barrel `index.ts` where applicable)
  shared/        Cross-feature UI, formatting, and layout styles
  generated/     OpenAPI-generated TypeScript types (do not edit)
```

Feature modules under `src/features/`:

- `analysis` — contract/document analysis views
- `auth` — login, register, session expiry handling
- `casework` — dispute and casework panels
- `deals` — deal list, detail workspace, lifecycle actions
- `documents` — deal document upload and management
- `fulfillment` — evidence submission and review
- `funding` — funding plan UI
- `invitations` — incoming and deal-scoped invitations
- `organization` — legal entity workspace and membership bootstrap
- `ratification` — ratification packages and confirmations
- `readiness` — platform/health status (development)
- `review` — manual review rules and confirmations
- `settlement` — simulated settlement panel
- `videoAnalysis` — evidence video analysis status

## Authentication and routing

- `/register`, `/login` — account flows with CSRF-protected mutations
- `/app` — protected workspace (legal entity selection and navigation shell)
- `/app/deals`, `/app/deals/:dealId` — deal list and detail
- `/app/invitations` — incoming invitation inbox
- `/` — redirects from the verified current-user result

State-changing requests fetch a fresh CSRF token from
`GET /api/v1/security/csrf`. Same-origin credentials are used; client code never
reads the HttpOnly session cookie. Scoped deal and organization requests send
`X-M4Trust-Legal-Entity-Id` from the versioned `sessionStorage` selection key.

## Local configuration

Requires Node.js 22.12 or newer. Copy `.env.example` to `.env` and set
`CORE_API_PROXY_TARGET` to the local Core API origin. The variable is read only
by `vite.config.ts`; it has no `VITE_` prefix and is never bundled into client
code. Browser requests remain relative:

- `/api/*` proxies to the configured Core API during development.
- `/actuator/*` proxies to the same target during development.

The readiness screen is available only at `/status` in a Vite development build.
Production routing neither renders this screen nor depends on Actuator.

## Commands

```bash
npm ci
npm run generate:api
npm run dev
npm run lint
npm run format
npm run format:check
npm run test
npm run typecheck
npm run build
```

`npm run generate:api` reads `../contracts/openapi/core-api-v1.yaml` and writes
`src/generated/core-api.d.ts`. The `typecheck` and `build` scripts run generation
before TypeScript. Generated types are never edited manually.

## Production web-edge image

Build the Caddy image from the repository root so the frontend build can read
the committed Core API OpenAPI document:

```bash
docker build -f frontend/Dockerfile -t m4trust-web-edge:<commit-sha> .
```

At runtime, `CORE_API_ORIGIN` must point to the Core API's private HTTP origin
and `PORT` supplies the listener port. Caddy serves `/healthz`, immutable static
assets, and SPA deep-link fallback. Only `/api/*` is reverse-proxied;
`/actuator/*` is explicitly answered with `404` and is never part of the public
edge surface.
