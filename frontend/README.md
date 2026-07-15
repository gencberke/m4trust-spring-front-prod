# M4Trust frontend

Vite + React + TypeScript foundation for the same-origin M4Trust web client.
The root route renders the real Core API readiness state with React Router and
TanStack Query; there is no mock or fallback response.

## Local configuration

Requires Node.js 22.12 or newer. Copy `.env.example` to `.env` and set
`CORE_API_PROXY_TARGET` to the local Core API origin. The variable is read only
by `vite.config.ts`; it has no `VITE_` prefix and is never bundled into client
code. Browser requests remain relative:

- `/api/*` proxies to the configured Core API during development.
- `/actuator/*` proxies to the same target during development.

## Commands

```powershell
npm install
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
