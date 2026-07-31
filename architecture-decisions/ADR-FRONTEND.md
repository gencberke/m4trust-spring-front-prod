# Frontend boundary and delivery policy

## Status

Accepted (2026-07-28). Merged `main@13d8f0a` (PR #53).

## Context

The React application is a browser client of the Core API. It must present
server-authoritative business behavior without becoming an alternate authority
for lifecycle, authorization or AI orchestration.

## Decisions

### Interface boundary

- Generate API declarations from committed OpenAPI and consume those types at
  the application boundary.
- Browser traffic reaches Core through the same-origin application boundary.
- The frontend does not call an AI service directly, create AI jobs directly or
  read AI-worker storage directly.
- The backend remains authoritative for available actions, lifecycle state,
  authorization, tenant visibility and business error semantics.
- Render server-provided action and lifecycle projections; do not infer allowed
  operations from status strings in the browser.
- Keep public request, response and error semantics in the contract rather than
  duplicating them in component documentation.

### Session and request handling

- Use the server-side browser session; JavaScript does not own bearer tokens.
- Send credentials only through the configured same-origin Core API client.
- Obtain and refresh CSRF material only for unsafe authenticated requests.
- Treat the active legal-entity selector as context, never as authorization
  proof; the server validates membership.
- Reset client session state and route to the unauthenticated boundary when the
  server reports session expiry or equivalent authentication loss.
- Display stable server Problem Details safely; do not manufacture business
  success from transport ambiguity.

### Structure

- Keep app, page, feature and shared boundaries clear enough that ownership is
  visible from the import path.
- Put reusable pure utilities in shared code and keep feature orchestration near
  the feature that owns it.
- Prefer generated API types over handwritten duplicated DTOs.
- Avoid speculative client-side state frameworks or abstractions.
- Keep styles and presentation implementation local to the component boundary
  where practical; style organization is not a domain model.

### Test policy

- Retain tests for Core API plumbing, CSRF/error/session behavior and complex
  pure helpers such as canonical money handling.
- Retain one route/session boundary proof for authenticated, unauthenticated and
  expired-session behavior.
- Rely on TypeScript compilation, generated-type drift, lint, formatting and
  production build for broad client integrity.
- Do not keep tests for isolated badges, modals, button wiring, simple form
  rendering or presentation-only component details.
- Do not add a browser automation suite unless a new accepted plan establishes
  a browser-only critical risk that lower-level evidence cannot prove.

### Review checkpoints

- Confirm generated types remain clean after a contract change.
- Confirm unsafe requests retain CSRF behavior.
- Confirm an expired session cannot leave protected client state active.
- Confirm legal-entity context is passed without being trusted locally.
- Confirm a UI action is backed by server-provided availability.
- Confirm an AI capability is reached only through Core behavior.
- Confirm errors remain safe when transport details are unexpected.
- Keep visual changes free to evolve without adding presentation matrices.
- Keep reusable pure helpers independently testable where their rules are complex.
- Escalate a new browser-only critical risk before creating an E2E suite.

### Boundaries not owned here

- Payment, settlement and lifecycle decisions remain Core concerns.
- Contract evolution remains contract-first review work.
- AI-provider presentation choices belong to the separate AI owner.
- Deployment routing and secret provisioning belong to Deployment authority.
- Component styling does not define business authorization.
- Client caches do not become an alternative source of accepted state.

## Non-negotiables

- No browser token authority or direct AI integration.
- No client-side authorization or lifecycle authority.
- No handwritten divergence from generated public-contract types.
- No secret, provider credential or private service address in browser output.

## Consequences

Frontend validation becomes smaller and faster while retaining the session and
contract boundary that protects real users. Presentation changes rely on build,
lint and focused boundary evidence rather than a large render-test inventory.

## Change triggers

Revise this ADR when the browser/Core trust boundary, session model, API
generation model or frontend ownership model changes.
