> **Not project state.** This file is informational handoff only. Authoritative accepted state is `docs/plan/CURRENT.md` and `docs/plan/ready|done/`. Do not treat this document as implementation authority.

# AI Integration Observations — Non-Authoritative Handoff

- Status: `informational`
- Reviewed baseline: `RqyRen/M4Trust-Gayrettepe@bf985e7a396f82f437ad0dcacdcad5bb431fe18d`
- Owner of AI implementation decisions: AI repository owner/team
- Main-team scope: shared Contracts, Spring producer/consumer behavior, frontend
  projections, and Core/Web deployment integration

## Authority boundary

This document is not an ADR, ready plan, task packet, acceptance, provider/model
selection, or authorization to change the AI repository.

The main team may:

- maintain and review the committed shared OpenAPI/AsyncAPI/JSON Schema authority;
- test the Spring producer/consumer against those contracts;
- run a read-only compatibility check against a named AI repository revision;
- report an exact schema, fixture, event, or lifecycle incompatibility;
- suggest production/security improvements to the AI owner.

The main team may not select or replace AI providers, models, weights, revisions,
frameworks, worker topology, concurrency, container layout, CI/CD, or deployment
configuration. Any AI-side change requires the AI owner to propose and approve it.

## Contract boundary that remains binding

The previously accepted ADR-001, ADR-002, ADR-003, ADR-006 and ADR-012 rules remain
the cross-team authority:

- Spring owns users, authorization, jobs and business state;
- AI output is advisory and cannot directly mutate Deal/payment/fulfillment/
  casework state;
- communication uses reviewed asynchronous contracts and at-least-once,
  duplicate-safe delivery;
- raw document/video bytes, provider payloads and secrets do not enter broker
  messages;
- a breaking shared-contract change requires joint review before implementation;
- neither team silently changes the contract to fit its local implementation.

## Observations for AI-owner review

The audit of the named baseline produced the following non-binding observations.
They must be revalidated by the AI owner because the main team does not own the
implementation or its operational context:

1. Model license/provenance and exact runtime revision/file-hash evidence was not
   discoverable for every referenced local model.
2. Some model/dependency acquisition appeared capable of floating or occurring at
   runtime rather than from an owner-approved immutable supply-chain manifest.
3. Masking and retrieval dependency failures appeared capable of continuing toward
   external processing; the AI owner should review the desired failure policy.
4. Roboflow configuration appeared to include community identifiers and credential
   transport/log-redaction questions that merit owner review.
5. RabbitMQ heartbeat, blocking inference, reconnect and graceful-shutdown behavior
   merit production load/failure testing by the AI owner.
6. Provider privacy, retention, DPA, logging and data-region evidence was not part
   of the main repository evidence set.

No replacement model is selected here. In particular, neither
`savasy/bert-base-turkish-ner-cased` nor any previously suggested alternative is
accepted or rejected by the main team. BGE, NER, OpenAI and Roboflow choices and
their exact revisions remain entirely with the AI owner and user.

## Suggested evidence from the AI owner

If AI-enabled flows are included in a production pilot, the main team recommends
receiving a concise evidence packet containing:

- AI repository release SHA and AI owner approval;
- shared-contract revision/digest supported by that release;
- producer/consumer fixture and duplicate-delivery compatibility results;
- the AI owner's statement that required privacy/security/provider approvals are
  complete;
- staging endpoint/broker environment identity and a contact/runbook owner;
- any proposed contract delta as a separate jointly reviewed contract change.

The AI owner chooses the internal proof format. Missing evidence blocks an
AI-enabled product-readiness claim, but it does not authorize the main team to
modify the AI repository or prescribe a substitute implementation.

## Main-team response to incompatibility

When a read-only check finds a mismatch, the main team records:

```text
main contract revision:
AI repository revision:
affected schema/event/fixture:
exact expected vs actual difference:
compatibility impact:
proposed contract discussion (if any):
AI owner acknowledgement:
```

Until jointly resolved, the main contract is not silently weakened and the
affected AI-enabled release gate remains open.
