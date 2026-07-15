# M4Trust frontend

Vite + React + TypeScript client for the same-origin M4Trust Core API. The
authentication flow uses the real Spring session endpoints through React Router
and TanStack Query; there is no mock user or browser-stored authentication state.

## Authentication routes

- `/register` creates an account and enters the protected application.
- `/login` restores access to an existing account.
- `/app` is protected by the verified `GET /api/v1/auth/me` result.
- `/` redirects from the verified current-user result.

Every register, login, and logout request first fetches a fresh CSRF token from
`GET /api/v1/security/csrf`. Requests use same-origin credentials; client code
never reads the HttpOnly session cookie or stores credentials in web storage.

## Local configuration

Requires Node.js 22.12 or newer. Copy `.env.example` to `.env` and set
`CORE_API_PROXY_TARGET` to the local Core API origin. The variable is read only
by `vite.config.ts`; it has no `VITE_` prefix and is never bundled into client
code. Browser requests remain relative:

- `/api/*` proxies to the configured Core API during development.
- `/actuator/*` proxies to the same target during development.

The readiness screen is available only at `/status` in a Vite development build,
and the `/actuator` proxy exists only on the development server. Production
routing neither renders this screen nor depends on Actuator. The production edge
must expose the same-origin `/api/*` surface without public Actuator endpoints.

## Commands

```powershell
npm ci
npm run generate:api
npm run dev
npm run typecheck
npm run build
```

`npm run generate:api` reads the reviewed
`../contracts/openapi/core-api-v1.yaml` contract and writes
`src/generated/core-api.d.ts`. The `typecheck` and `build` scripts run this
generation step before TypeScript. Generated public contract types are never
edited manually or replaced with parallel handwritten models.

The readiness payload is an Actuator operational model outside the public
OpenAPI contract, so its small local type lives with the readiness feature.
