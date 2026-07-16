# M4Trust frontend

Vite + React + TypeScript client for the same-origin M4Trust Core API. The
authentication flow uses the real Spring session endpoints through React Router
and TanStack Query; there is no mock user or browser-stored authentication state.

## Authentication routes

- `/register` creates an account and enters the protected application.
- `/login` restores access to an existing account.
- `/app` is protected by the verified `GET /api/v1/auth/me` result and renders
  the real legal-entity workspace.
- `/app/deals` lists and creates Deals for the active legal entity.
- `/app/deals/:dealId` renders Deal detail, edit, and cancel flows.
- `/` redirects from the verified current-user result.

Every register, login, and logout request first fetches a fresh CSRF token from
`GET /api/v1/security/csrf`. Requests use same-origin credentials; client code
never reads the HttpOnly session cookie or stores credentials in web storage.
The shared API error path publishes `AUTH_SESSION_EXPIRED` once, and the router
boundary clears the verified current-user query before redirecting to `/login`.
Invalid-credential responses do not trigger this global session-expiry path.
Register and login success responses remain the narrower `PublicUser` contract;
the client always re-fetches `/auth/me` before entering the protected workspace
so membership bootstrap state is never synthesized from an incomplete response.

## Legal entity workspace

The `/app` workspace lists the authenticated user's legal-entity memberships,
supports legal-entity creation, and shows the selected entity detail and member
list. Creating an entity refreshes both current-user and membership queries,
then selects the created entity so the resulting ADMIN membership is visible.

The selected legal-entity UUID is the only organization value stored by the
browser. It uses the versioned `m4trust:selected-legal-entity-id:v1`
`sessionStorage` key, preserving refreshes while allowing separate tabs to keep
different active contexts. Storage access is guarded because browser storage
may be unavailable; the current tab keeps working from a validated in-memory
selection, while refresh persistence is naturally unavailable in that case.
User data, membership objects, credentials, and session cookies are never
stored there.

The shared API layer automatically adds the selected UUID as
`X-M4Trust-Legal-Entity-Id`. This value is client context rather than proof of
authorization; the Core API validates membership for every scoped request. A
`LEGAL_ENTITY_ACCESS_DENIED` or `LEGAL_ENTITY_NOT_FOUND` scoped response clears
the stale selection and asks the user to choose again. Logout and centralized
session expiry also clear the selection. The authenticated layout keeps the
switcher, account controls, and Organization/Deals navigation stable across all
protected nested routes.

## Deal workspace

Deal list and detail queries are keyed by the active legal-entity UUID. List
keys also contain the exact status filter, page, page size, and allowlisted sort,
so a tab or entity switch cannot reuse another scoped result. With no active
legal entity, the Deal routes show an actionable selection state and issue no
Deal request.

The list supports exact status filtering, `createdAt`/`title` sorting, stable
page controls, first-use and filtered empty states, and creation through the
real Spring API. Successful creation returns the view to the newest unfiltered
page and invalidates the active entity's Deal list so the new record is visible.
Detail lifecycle and available actions are rendered exactly as returned by the
backend; the client does not derive them from status combinations.

Deal updates retain the loaded server `version` and send it as
`expectedVersion`. An empty edit description is sent as explicit `null`. On
`DEAL_STALE_VERSION`, attempted form values remain visible and no automatic
overwrite or resubmission occurs. The user must choose “Güncel veriyi yükle”;
only then is detail refetched and the form reset from the newer projection.
`DEAL_NOT_FOUND` uses a non-disclosure state without clearing a valid active
entity, while invalid legal-entity context still clears the stale selection.

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
