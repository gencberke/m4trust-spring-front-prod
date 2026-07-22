# Slice 15 — Production Reconciliation and Readiness

- Status: `ready` — human-approved on 22 July 2026
- Approval authority: founder/user instruction to prepare the complete planner
  handoff before a separate implementer starts
- Baseline: `main@b2ae2dee63d58a44cddacb49814f17049c7720d3`
- Accepted decisions: ADR-001–ADR-019
- Cross-repository AI baseline:
  `RqyRen/M4Trust-Gayrettepe@bf985e7a396f82f437ad0dcacdcad5bb431fe18d`
- Plan ownership: planner; implementer may execute only assigned task-packet phases
- Completion boundary: implementation completion is not production/pilot acceptance

## 1. Amaç ve kullanıcı sonucu

M4Trust'ın accepted Slice 0–14A davranışlarını yeniden yazmadan, gerçek full-stack
staging ve kontrollü production pilot için bilinen contract, authentication,
storage, AI runtime, release ve recovery boşluklarını kapatmak.

Observable user result:

1. Kullanıcı yalnız güvenli invitation link'iyle verified account açar, legal
   entity membership invitation'ını ayrıca kabul eder ve session ile giriş yapar.
2. Document/evidence upload malware scan temizlenmeden finalize, download veya AI
   action'a açılamaz.
3. Document extraction OpenAI + local masking/RAG; video analysis private Roboflow
   ile asynchronous çalışır ve AI sonucu advisory kalır.
4. Aynı reviewed artifact digest'leri full-stack staging'den production pilot'a
   promote edilir; rollback/restore/alert kanıtı bulunur.
5. Public API runtime, committed OpenAPI, frontend generated types ve AI shared
   contracts sessizce drift edemez.

## 2. Kapsam ve sınırlar

### In scope

- Public/internal contract error catalog, runtime OpenAPI structural comparison,
  generated-client drift and deterministic contract bundle digest.
- Core production image contract resources and root build context.
- ADR-017 invite-only account/member onboarding, password recovery, database-backed
  login throttling, notification outbox, Postmark adapter and frontend flows.
- ADR-018 AWS S3/GuardDuty clean-scan gate for Deal documents and fulfillment
  evidence, exact-version cleanup and IaC.
- ADR-019 AI provider/privacy/model/worker hardening in the AI repository.
- ADR-016 production profiles, private topology config, Caddy security, health,
  release manifest/workflows, migration/rollback/restore, observability and runbooks.
- Full local/CI integration proof and deployable staging/production configuration.
- Planner-owned staging browser acceptance and production pilot evidence.

### Out of scope

- Slice 14B settlement/release implementation; ADR-014 is accepted but code stays
  in the post-fix roadmap.
- MFA, casework resolution/operator workflow, data erasure/retention automation,
  ACTIVE cancellation, refund/reversal, multi-milestone, AI work management and
  enterprise SSO.
- Real payment/provider/custody/payout behavior.
- Accepted evidence/document overwrite or deletion.
- Implementer creation of AWS/Railway/Postmark/OpenAI/Roboflow/Grafana accounts,
  production secrets, legal/DPA approval, DNS changes or production traffic.
- Claiming the 7-day pilot passed without real elapsed-time evidence.

If implementation needs any out-of-scope behavior, return `REPLAN`; do not build a
fallback or widen the task.

### Implementation-entry and external-gate matrix

| Input/evidence | Owner | Required before | Current state |
|---|---|---|---|
| ADR-014–ADR-019, this ready plan, main/AI base SHAs | Planner/user | 15-T01 | Closed |
| Exact BGE/NER revisions, runtime file hashes and licenses | Planner | 15-T05 | Closed in ADR-019 |
| Postmark server/stream, verified domain, DKIM/SPF/DMARC and webhook source policy | User/planner | live P5 staging acceptance | External gate; not a code-start blocker |
| AWS staging/production S3, KMS and GuardDuty roles | User/planner | P10 staging deployment | External gate; P6 IaC remains implementable offline |
| OpenAI project/DPA/retention/region/privacy approval reference | User/privacy owner | real-provider P8/P10 evidence | External gate; processing remains disabled |
| Private Roboflow workspace plus exact owned model version IDs/DPA/golden set | User/privacy owner | real-provider P8/P10 evidence | External gate; community IDs remain rejected |
| Railway production project/Pro registry access/PITR and Grafana Cloud endpoints | User/planner | P10 operative acceptance | External gate; config/runbooks remain implementable |
| Pilot cohort and business-data retention/legal sign-off | User/legal/product | pilot entry / broad release | External acceptance; implementer cannot choose |

No secret or external account value is committed. An unavailable external gate is
reported `NOT_RUN`/blocked at its named checkpoint; it cannot be replaced by mock
evidence or waived by the implementer.

## 3. Kararlar ve ilgili ADR'ler

- Contracts and runtime authority: ADR-002 §§24–25; ADR-006 §§13–16, 42–47;
  ADR-016 §§2.4–2.6.
- Audit/event/dispatch transaction semantics: ADR-003 §§24–26; ADR-015.
- Authentication/session/non-disclosure: ADR-005 §§5–17, 20–23; ADR-017.
- Direct storage, immutable evidence and malware gate: ADR-001 §6; ADR-011
  §§2.3–2.6; ADR-018.
- AI ownership/advisory boundary: ADR-001 §§2, 7, 10, 16–19; ADR-002 §§17–30;
  ADR-012; ADR-019.
- Deployment, migration, recovery and promotion: ADR-007 §§21–43; ADR-016.
- ADR-014 changes decision authority only. No settlement/release API or state is
  implemented in this plan.

Binding implementation choices:

- Main shared contracts are authoritative.
- Production registration is invite-only; Postmark is the notification adapter.
- Production object storage is AWS S3/KMS/GuardDuty.
- Production AI scope is OpenAI snapshot + private Roboflow + offline BGE-M3/NER.
- Railway EU West is the runtime; only web-edge is public.
- Build once and promote exact OCI digests; no production rebuild/latest tag.
- RPO `<=15m`, RTO `<=4h`; pilot is 7 days/max 3 entities/15 users.

## 4. Public interface, state ve data etkisi

### 4.1 Closed error catalogs

`ProblemDetail.code` references closed `ApiErrorCode`; `FieldError.code` references
closed `FieldErrorCode` in `core-api-v1.yaml`. Catalog contents are the exact union
of every code documented by endpoint/reusable response plus global codes. Contract
validation rejects a runtime/frontend code outside the catalogs and rejects an
unused Java enum member not present in contract.

Fulfillment runtime must emit the existing granular contract codes at the
authorization boundary:

- `LEGAL_ENTITY_NOT_FOUND`;
- `DEAL_NOT_FOUND`;
- `FULFILLMENT_NOT_FOUND`;
- `EVIDENCE_NOT_FOUND`.

Undocumented `DEAL_OR_LEGAL_ENTITY_NOT_FOUND_OR_HIDDEN` and
`FULFILLMENT_OR_EVIDENCE_NOT_FOUND_OR_HIDDEN` are removed from Core/frontend.
Documented `CASEWORK_NOT_FOUND_OR_HIDDEN` remains valid.

New stable codes:

- `AUTH_REGISTRATION_CLOSED`;
- `AUTH_INVITATION_NOT_FOUND_OR_INVALID`;
- `AUTH_INVITATION_STATE_CONFLICT`;
- `AUTH_PASSWORD_RESET_NOT_FOUND_OR_INVALID`;
- `MEMBER_INVITATION_NOT_FOUND_OR_HIDDEN`;
- `MEMBER_INVITATION_ACTIVE_EXISTS`;
- `MEMBER_INVITATION_STATE_CONFLICT`;
- `UPLOAD_SCAN_PENDING`;
- `UPLOAD_REJECTED_MALWARE`;
- `UPLOAD_SCAN_UNAVAILABLE`.

Status mapping is closed:

| Code | HTTP status |
|---|---:|
| `AUTH_REGISTRATION_CLOSED` | 403 |
| `AUTH_INVITATION_NOT_FOUND_OR_INVALID` | 404 |
| `AUTH_INVITATION_STATE_CONFLICT` | 409 |
| `AUTH_PASSWORD_RESET_NOT_FOUND_OR_INVALID` | 404 |
| `MEMBER_INVITATION_NOT_FOUND_OR_HIDDEN` | 404 |
| `MEMBER_INVITATION_ACTIVE_EXISTS` | 409 |
| `MEMBER_INVITATION_STATE_CONFLICT` | 409 |
| `UPLOAD_SCAN_PENDING` | 409 |
| `UPLOAD_REJECTED_MALWARE` | 422 |
| `UPLOAD_SCAN_UNAVAILABLE` | 503 |

Login throttling continues to return `AUTH_INVALID_CREDENTIALS` to avoid account
existence disclosure.

### 4.2 Account and membership API

Requests/responses:

```text
POST /api/v1/auth/invitations/inspect
request:  { token }
200:      { purpose, maskedRecipient, expiresAt, existingAccount }

POST /api/v1/auth/invitations/accept
request:  { token, displayName, password }
200:      existing AuthenticationSuccess contract

POST /api/v1/auth/password-reset/request
request:  { email }
202:      empty body, same result for every syntactically valid email

POST /api/v1/auth/password-reset/confirm
request:  { token, newPassword }
204:      all prior server sessions revoked; no automatic new session

GET  /api/v1/legal-entities/{legalEntityId}/member-invitations
200:      stable zero-based paged initiator/admin projection

POST /api/v1/legal-entities/{legalEntityId}/member-invitations
request:  { recipientEmail, role: ADMIN|MEMBER }
201:      invitation projection + Location

GET  /api/v1/member-invitations/incoming
200:      stable zero-based paged recipient projection

POST /api/v1/member-invitations/{id}/accept|reject|revoke
request:  { expectedVersion }
200:      terminal invitation projection

POST /api/v1/member-invitations/{id}/resend
request:  { expectedVersion }
200:      same PENDING invitation identity with incremented version/expiry
```

Authenticated member invitation mutations require session CSRF and
`Idempotency-Key`. Pre-auth auth endpoints use strict same-origin/fetch-metadata,
closed CORS and ADR-017 abuse throttles instead of cookie/session authority.
Entity-admin actions use validated active legal-entity context. Recipient
accept/reject is user-scoped and does not use that header. Invite state is
`PENDING|ACCEPTED|REJECTED|REVOKED|EXPIRED`; expiry may be materialized on read/
mutation but no free status setter exists.

`/auth/register` remains in v1 for compatibility; production returns the stable
closed error and creates no session/user.

### 4.3 Upload finalize behavior

Existing document/evidence status enums do not change. Finalize maps exact S3
version scan tag per ADR-018:

- no tag: `409 UPLOAD_SCAN_PENDING`;
- clean: existing finalize success;
- threat: `422 UPLOAD_REJECTED_MALWARE`;
- unsupported/access-denied/failed: `503 UPLOAD_SCAN_UNAVAILABLE`.

No download/AI endpoint shape changes beyond the closed error catalog; server
eligibility ensures only clean finalized versions are reachable.

### 4.4 Internal contract interface

Add reviewed `contracts/openapi/core-internal-v1.yaml` with private:

```text
GET /internal/v1/contracts
```

Response uses ADR-016's exact `service`, 40-hex `releaseRevision`, prefixed digest
and ordinal `files[{path,sha256}]` projection; no filesystem path, credential or
business data. Existing AI-internal endpoint aligns to the same digest algorithm
and requires private service authentication.

### 4.5 Persistence and migrations

V1–V22 remain unchanged. Main implementation adds forward-only migrations in
phase order, using the next available version at task start:

- account invitation, legal-entity member invitation and constraints;
- user verification timestamp/state without auto-verifying historical rows;
- password-reset token and principal-session revoke support;
- bounded login-throttle state;
- notification outbox/delivery identity;
- sanitized upload scan observation and physical-cleanup timestamps/lease state.

Token plaintext, provider payload, malware signature and raw content never enter
business/audit tables. S3 tag remains authoritative at finalize; DB scan status is
operational observation only.

### 4.6 Compatibility

- Existing local/test register clients remain valid under non-production config.
- Existing v1 frontend is regenerated; no handwritten parallel DTOs.
- Existing finalized object is never auto-marked clean. Staging/production launch
  inventory must scan/re-upload it.
- Shared AI event schemas remain wire-compatible; this plan changes validation/
  packaging/producer runtime, not their payload semantics.
- Database changes are additive/expand-only; previous accepted image remains
  runnable until an explicit later contract phase.

## 5. Implementation phases

Packet release sequence is fixed but only the next packet is executable. Planner
issues each later packet with a new exact accepted base after reviewing the prior
packet; placeholders below are not implementation authority:

| Packet | Repository | Assigned phases | Release dependency |
|---|---|---|---|
| `15-T01` | main | P1 | this planner commit |
| `15-T02` | main | P2–P3 | accepted 15-T01 base |
| `15-T03` | main | P4–P5 | accepted 15-T02 base |
| `15-T04` | main | P6–P7 | accepted 15-T03 base |
| `15-T05` | AI | P8 | accepted P2 authority + exact AI baseline |
| `15-T06` | main | P9 | accepted 15-T01–15-T05 evidence |
| `15-T07` | main; accepted AI head is validation input, not write scope | P10 | accepted P1–P9 heads |

The user owns every handoff/merge. No implementer moves this plan, updates
`CURRENT.md`, folds two repository writes into a singular branch/base packet, or
starts the next packet from an unreviewed head.

### P1 — Close public error authority

Outcome:
Committed OpenAPI owns closed API/field error catalogs and fulfillment emits only
documented granular codes; frontend no longer depends on undocumented codes.

Direction:

- Change `core-api-v1.yaml`, validator expectations, contract README/CHANGELOG and
  generated TypeScript before Java/frontend behavior.
- Derive Java error enums from or exact-set validate them against the contract;
  do not maintain an unchecked third list.
- Preserve endpoint-specific 403/404 non-disclosure ordering from ADR-006/011.
- Update frontend stable-code mapping and focused error states from generated types.

Depends on:
None.

Exit checks:

- Contract validator and expected-invalid catalog tests pass.
- Java exact-set test passes.
- Focused fulfillment authorization/error tests pass.
- Generated TypeScript clean-diff, frontend typecheck/build pass.
- Repository search finds no undocumented combined fulfillment code.

### P2 — Package and identify the shared contract bundle

Outcome:
Packaged Core and the language-neutral reference implementation load the exact
reviewed contract bundle and produce the digest that P8 must consume in AI.

Direction:

- Implement ADR-016's closed semantic inclusion globs and exact committed-byte,
  POSIX-path, ordinal-sort, UTF-8/LF manifest algorithm; do not broaden the bundle
  to docs/scripts/dependency files.
- Add `core-internal-v1.yaml`, endpoint implementation and validator coverage.
- Change Core Docker build to monorepo-root context and copy schemas into JAR;
  production image smoke must open every runtime schema.
- Update main authority/sync expectations and the exact three ADR-016 Core OCI
  labels; P8 updates the AI-owned manifest/labels and main remains authority.
- No runtime filesystem dependency on repository-relative `../../contracts`.

Depends on:
P1.

Exit checks:

- Packaged JAR/image schema load test passes.
- Main validator/Core/reference digest golden test returns the same value when run
  over both repositories' unchanged contract bytes.
- Internal OpenAPI validation and no-public-route test pass.
- Internal contract endpoint rejects missing/wrong probe token and accepts
  active/rotation-overlap tokens without logging either value.
- Non-root container smoke passes.

### P3 — Enforce runtime OpenAPI and main-side consumer CI

Outcome:
Spring runtime surface, committed public contract, frontend output and the
main-authority side of AI consumer validation cannot drift in a mergeable main PR.
P8 installs the reciprocal AI-repository PR gate.

Direction:

- Generate runtime OpenAPI only in test/contract profile; production docs routes
  remain disabled/private.
- Normalize/compare paths, methods, parameters, security, status, media type and
  schema references; descriptions/example ordering are non-semantic.
- Application code changes that can affect API run contracts workflow.
- Frontend workflow regenerates types and fails on dirty diff.
- Main contract PR checks out the exact AI baseline and runs its producer/consumer
  validation against the proposed main bundle without writing the AI repository.

Depends on:
P1, P2.

Exit checks:

- Deliberate test fixture drift fails each gate.
- Main workflow fails when the pinned AI consumer rejects a proposed contract or
  its synchronized contract bytes differ.
- Production profile has no public Swagger/OpenAPI route.
- Workflows are least-privilege and use pinned action SHAs/versions.

### P4 — Implement invite-only identity foundation

Outcome:
Production registration closes, invitation/reset/throttle persistence and backend
APIs work with correct transaction, authorization and session semantics.

Direction:

- Apply contract-first account/member/reset endpoints and new errors.
- Add forward-only additive migrations and DB constraints for one active member
  invite per entity/e-mail and bounded token/throttle state.
- Use 32-byte random tokens, SHA-256 persistence, 72h invite/30m reset TTL and
  request-body token handling.
- Preserve account activation, membership consent and Deal invitation as separate
  transactions/actions.
- Login throttle is PostgreSQL-authoritative across replicas; trusted IP comes
  only through ADR-016 proxy processing; independent keyed e-mail/IP subjects,
  reset/invalid-token abuse limits and responses remain enumeration-safe.
- Reset invalidates every Spring Session for the principal.
- Add one-shot operator bootstrap without business/casework/payment authority.

Depends on:
P1.

Exit checks:

- Migration clean/upgrade and constraint tests pass.
- Invite inspect/accept/replay/expiry/revoke/resend and existing-account takeover
  tests pass.
- Membership actor/non-disclosure/idempotency/concurrency matrix passes.
- Reset generic request, invalid token and all-session revoke tests pass.
- 5/15/15 multi-instance e-mail/IP throttle, shared-IP non-reset and reset/token
  abuse-limit tests pass without account disclosure.
- Production bootstrap/register guards pass.

### P5 — Deliver transactional notifications and onboarding UI

Outcome:
Invitation/recovery requests are delivered through durable Postmark notification
work and users can complete the approved browser flows.

Direction:

- Mutation + audit/idempotency + notification outbox are atomic per ADR-015;
  Postmark call is relay-owned and outside the transaction.
- Persist no plaintext token in audit/public projection. Use the exact ADR-017
  AES-256-GCM versioned key-ring envelope; clear ciphertext after send/expiry and
  test active/decrypt-only rotation and unknown-key startup failure.
- Implement Postmark transactional stream, template versions, retry/DLQ and
  idempotent Basic-Auth/source-allowlisted bounce webhook.
- Implement ADR-017's closed delivery states/schedules and revalidate linked
  token generation before send; stale resend/revoke work cancels and destroys
  ciphertext. Document the bounded duplicate-email window without weakening
  single-use business consumption.
- Build SPA routes whose secret is in URL fragment, immediately removed from
  browser history after capture, and posted in request body.
- Remove production register navigation; add invitation, reset and member-inbox/
  admin-management loading/error/terminal states from generated types/actions.
- Deal invitation creates notification/onboarding linkage without auto-accept.

Depends on:
P4.

Exit checks:

- Transaction rollback/outbox, relay retry/duplicate and webhook duplicate tests
  pass.
- Revoke-before-send, resend-vs-old-outbox, expiry-during-retry and
  send-success/crash-before-commit tests preserve token/business safety.
- Logs and browser URL/history tests contain no token/provider secret.
- Frontend typecheck/build and focused two-session component/integration tests pass.
- Postmark disabled/missing production config fails closed.

### P6 — Add S3/GuardDuty quarantine contract and infrastructure

Outcome:
Production storage policy and application port represent exact-version scan
results without changing evidence/document business lifecycles.

Direction:

- Add finalize errors to committed OpenAPI and generated client first.
- Extend storage technical port with exact-version tag/status lookup; domain code
  sees closed provider-neutral scan outcome.
- Require finalize's exact non-null S3 `versionId`/ETag and compare HEAD
  `ChecksumSHA256` to the contract hex digest; CORS exposes only those required
  response headers.
- Add AWS Terraform for separate staging/production S3, KMS, GuardDuty plan, IAM,
  public-access block, CORS, lifecycle, logs and tag-based read deny.
- Presigned PUT cannot provide scan tag/ACL; only GuardDuty role can tag.
- Local/CI scanner adapter is explicit and production bootstrap rejects it.

Depends on:
P1, P2.

Exit checks:

- Contract/generated-client checks pass.
- Terraform format/validate/policy tests pass without credentials or state files.
- Adapter tests cover all five GuardDuty outcomes, missing tag and version mismatch.
- Browser/adapter tests reject missing/null version, stale ETag, checksum encoding
  mismatch and overwritten-key races.
- Policy tests reject public/non-KMS/tag-forgery/non-clean-read paths.

### P7 — Enforce scan gate, cleanup and frontend behavior

Outcome:
Document/evidence finalize, download and AI initiation fail closed until the exact
object version is clean; eligible orphan/quarantine objects are recoverably cleaned.

Direction:

- Perform metadata/checksum/version/scan lookup outside DB transaction; lock and
  revalidate pending intent/tuple in the existing finalize transaction.
- Map outcomes exactly; never expose provider detail/signature/key.
- Gate every download/analysis path at finalized clean business state plus storage
  policy defense in depth.
- Add multi-replica lease-based cleanup: expired pending after 24h; unsafe
  quarantine after 7d; accepted/rejected/finalized history never targeted.
- External delete happens outside transaction; result application is idempotent.
- Frontend handles pending with bounded refetch, threat as terminal re-upload
  guidance and unavailable as safe retry/support state.

Depends on:
P6.

Exit checks:

- Document/evidence pending/clean/threat/unavailable and stale-race tests pass.
- AI/download cannot observe non-clean objects.
- Cleanup eligibility, duplicate lease/delete and immutable-history tests pass.
- Frontend typecheck/build and focused upload-flow tests pass.

### P8 — Harden the AI producer and worker repository

Outcome:
AI API/worker images use pinned providers/models, privacy gates and reconnect-safe
async consumption while preserving canonical shared contracts.

Direction:

- Branch from exact AI baseline named in this plan; do not edit main repo contracts
  from the AI task.
- Pin Python 3.13 image digest and frozen/hash-locked Linux dependencies.
- Split lightweight `ai-api` and model-bearing `ai-worker` targets; non-root and
  offline runtime.
- Vendor-lock the exact ADR-019 BGE-M3 dense-only and licensed Turkish NER
  revisions/file allowlists; no implementer-selected revision or pickle weight.
- Replace the current Savasy NER reference with the accepted Akdeniz27 adapter;
  regenerate/pin legal-corpus embeddings with source/adapter/model digests and
  prove representative masking/retrieval golden parity before switching.
- Pin OpenAI snapshot, set `store=false`, fail closed on masker/RAG error and prove
  zero provider call.
- Implement closed `EXTERNAL_AI_PROCESSING_MODE`; production defaults disabled and
  readiness requires the release-manifest privacy/DPA/notice approval reference.
- Add cleartext masking sentinels plus representative Turkish NER report; never
  label masking as anonymization or let the implementer choose legal acceptance.
- Require private Roboflow allowlist, Bearer auth and redaction; reject current
  community IDs in production.
- Replace blocking Rabbit consumption with robust aio-pika/event-loop heartbeat,
  bounded inference executor, confirms, reconnect, graceful drain and Redis lease/
  PENDING_PUBLISH recovery.
- Preserve canonical job identity but do not claim exactly-once provider calls;
  give each bounded provider attempt a privacy-safe client request ID/metric and
  keep duplicate results business-idempotent after ambiguous timeout/Redis loss.
- Readiness requires broker, Redis, contracts and offline models.
- Update the AI-owned sync manifest/OCI labels and add an every-PR workflow that
  checks out its pinned main authority, exact-diffs bundle bytes and runs consumer
  validation; weekly drift remains supplemental only.

Depends on:
P2. May run in parallel with P4–P7 in the separate AI repository.

Exit checks:

- Existing and new AI unit/contract tests pass under Python 3.13.
- Masker/retrieval failure proves zero OpenAI calls.
- Every masking sentinel is absent from provider-request snapshots; external
  processing remains disabled without the accepted privacy approval reference.
- Provider URL/log tests prove no key/raw PII.
- Offline image/model hash/startup smoke passes with network unavailable.
- Model lock rejects any unlisted file/hash and corpus/model digest mismatch.
- Broker disconnect, long heartbeat, SIGTERM, duplicate and Redis-loss tests pass.
- Provider-timeout/Redis-loss evidence reports exact attempt counts while proving
  one canonical Spring job/result application and no invented success.
- AI/main contract digest and sync manifest match.

### P9 — Build production runtime, release and operations foundation

Outcome:
Repository contains deployable private topology configuration, immutable release
workflow, production guards, observability and executable recovery runbooks.

Direction:

- Add `application-production.yml` and base Rabbit/Redis/S3 mappings; validate all
  required config and transport mode at startup.
- Configure only web public; Caddy same-origin proxy, trusted Railway chain and
  ADR-016 security headers/path/body rules.
- Add Railway config-as-code for region, replicas, health, pre-deploy migration,
  overlap/drain/restart and root Docker contexts.
- Build four OCI artifacts once, publish GHCR digests, SBOM/provenance/release
  manifest; promotion consumes manifest digest, never rebuilds.
- Add OTel/Micrometer/Faro privacy filters and required metrics/alerts definitions.
- Make schedulers lifecycle-aware; tests shut down cleanly without forced JVM kill.
- Write command-complete runbooks for deploy, rollback, sibling PITR restore, S3
  version restore, DLQ/outbox reconciliation, provider degradation and secret
  rotation. Commands use placeholders for secrets/account IDs and include stop/
  verification gates.

Depends on:
P2, P3, P4, P6. AI artifact manifest fields depend on P8 evidence.

Exit checks:

- Config/bootstrap/profile tests and Caddy config test pass.
- Railway/AWS config validation and all image smoke builds pass.
- Release manifest/digest/SBOM/attestation workflow dry-run checks pass.
- Core full verify exits cleanly with no post-container scheduler access.
- Runbooks pass static safety review; no secret/destructive broad target exists.

### P10 — Integrate full-stack staging evidence and review handoff

Outcome:
Implementer provides a complete, reproducible validation bundle and deployable
staging candidate without claiming planner-owned browser/pilot acceptance.

Direction:

- Update local/CI integration orchestration for Core/Web/Postgres/Rabbit/Redis/
  S3-compatible storage/AI API/AI worker; real provider calls remain opt-in secret
  staging jobs, never default developer requirements.
- Run contract, migration, backend, frontend, AI, container, policy and focused
  failure/recovery suites.
- Produce redacted release manifest, image digests, migration ceiling, contract/
  model digests and exact commands/evidence locations.
- Do not deploy production, create external resources or run planner-owned browser
  acceptance unless a later task explicitly assigns it.

Depends on:
P1–P9.

Exit checks:

- All implementer validations in §7 pass or are honestly `NOT_RUN` for external
  credentials with a named planner gate.
- `git diff --check`, status and base-to-HEAD scope checks pass in both repos.
- `docs/plan/review/req-review.md` is `COMPLETED` only for assigned phases and
  says `Plan completion claim: NO`.

## 6. Gerçek browser ve operatif kabul

Owner: planner/user after implementer review. Implementer tests do not replace it.

### Staging prerequisites

- Exact reviewed release manifest deployed; all service contract/model digests
  match.
- Separate staging Postgres/Rabbit/Redis/S3/provider projects and no production
  secrets/data.
- Postmark staging stream/domain, private Roboflow models and OpenAI project pass
  external governance gates.
- Existing retained objects have clean exact-version scan evidence.

### Browser matrix

1. Production-like invite-only mode hides register; direct register POST returns
   `AUTH_REGISTRATION_CLOSED`.
2. New recipient opens fragment-token link, creates verified account, refreshes
   session and sees no token in URL/history/logs.
3. Recipient accepts legal-entity membership separately; wrong user/hidden entity/
   stale version do not disclose or mutate.
4. Existing verified recipient logs in instead of resetting account through the
   invitation token.
5. Reset request is indistinguishable for existing/unknown e-mail; confirm revokes
   all browser sessions.
6. Five failed logins trigger generic temporary block; recovery after exact window
   works without enumeration.
7. Deal invitation sends notification/onboarding but still requires separate Deal
   acceptance and legal-entity choice.
8. Document upload observes scan-pending, then clean finalize/download and real
   document extraction; masking/RAG/provider errors remain technical and safe.
9. Malware/unsupported object never finalizes/downloads/starts AI and prompts a new
   upload without exposing signature/provider detail.
10. VIDEO/MP4 evidence clean-finalizes, private Roboflow analysis remains advisory
    and manual buyer accept/reject behavior is unchanged.
11. Worker/broker restart preserves queued/unknown jobs and produces no duplicate
    business result.
12. Release candidate rollback restores the previous exact artifact while schema
    compatibility and login remain healthy.

### Operative acceptance

- Restore a production-like backup to sibling DB and prove RPO/RTO procedure.
- Restore an S3 version without exposing bucket/object publicly.
- Exercise DLQ/outbox reconciliation and provider-degradation runbooks.
- Verify alerts for public 5xx/latency, backup, queue age/DLQ, outbox age, AI
  failures and email bounce.
- After staging acceptance and external sign-off, run the 7-day/max 3 entity/15
  user production pilot. Any ADR-016 threshold breach stops the pilot.

## 7. Minimum invariant ve validation

### Main repository

```text
python contracts/scripts/validate_contracts.py
cd services/core-api && ./mvnw verify
cd frontend && npm ci && npm run typecheck && npm run build
docker build -f services/core-api/Dockerfile .
docker build -f frontend/Dockerfile frontend
git diff --check
```

Windows implementer may use repository-equivalent `python`, `mvnw.cmd` and
`npm.cmd` commands. CI workflow syntax, Terraform format/validate, Caddy validate,
container smoke and generated-clean-diff checks are additionally required by the
implemented scripts.

### AI repository

```text
python -m pip install --require-hashes -r <production-lock>
pytest
docker build --target ai-api .
docker build --target ai-worker .
git diff --check
```

Exact lock/script names are created in P8 and then used consistently in CI/docs.

### Invariants

- No accepted migration V1–V22 is modified.
- No endpoint is implemented before committed contract.
- No undocumented public error code survives.
- No external call runs in business DB transaction.
- No token/credential/raw content/provider payload appears in logs/audit/public API.
- No non-clean object is finalized, readable or AI-eligible.
- No AI error mutates fulfillment/casework/payment state.
- No production mock/sandbox/community model/floating model/latest image exists.
- No contract digest mismatch can become ready or promote.
- No planner-owned production/pilot claim is made by implementer.

## 8. Done tanımı

- [x] ADR-014–ADR-019 accepted and ADR index/FORBIDDEN synchronized
- [ ] P1 closed error authority accepted
- [ ] P2 packaged contract bundle/digest accepted
- [ ] P3 runtime/cross-repo drift gates accepted
- [ ] P4 invite-only backend identity accepted
- [ ] P5 notification/onboarding frontend accepted
- [ ] P6 S3/GuardDuty infrastructure and adapter accepted
- [ ] P7 scan enforcement/cleanup/frontend accepted
- [ ] P8 AI hardening accepted in the AI repository
- [ ] P9 production runtime/release/operations foundation accepted
- [ ] P10 full validation/review handoff accepted
- [ ] Planner independently reviews complete diffs and validations in both repos
- [ ] Planner-owned full-stack staging browser matrix passes
- [ ] Backup, restore, rollback, DLQ and alert drills pass
- [ ] External Postmark/OpenAI/Roboflow/AWS/Railway/Grafana gates are recorded
- [ ] Seven-day production pilot exits within ADR-016 SLO/security limits
- [ ] Retention/legal owner signs off before broad production
- [ ] Plan archived to `done/` and `CURRENT.md` updated only after every item above
