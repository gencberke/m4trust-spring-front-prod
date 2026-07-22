# Request for Review

## Metadata

| Field | Value |
|---|---|
| Task | 15-T04 |
| Plan | docs/plan/ready/15-railway-demo-reconciliation-and-deployment.md |
| Branch | codex/s15-t04-railway-demo-reconcile |
| Base | 3bc077fc863d06d0ea605a1d84e632b6b10a8ff4 |
| Revision | 3 |
| Phases claimed | P4 |
| Date | 2026-07-22 |

## Checklist

- [x] Scope matches the accepted task packet / revision request
- [x] No unrelated refactors or drive-by edits
- [x] Validation evidence recorded below
- [x] Open questions / risks called out (or explicitly none)
- [x] Ready for human review

## What changed

Rev 3 extends `MessagingBrokerBootstrapGuard` so that when messaging topology or
relay is enabled, `spring.rabbitmq.port` must parse as an integer in TCP range
`1..65535`. Malformed, zero, negative, and above-range values fail fast at
startup. Both-disabled broker-free startup is unchanged. Rev 1–2 behavior is
preserved.

## Validation evidence

Command / check:

```text
./mvnw -pl services/core-api "-Dtest=MessagingBrokerBootstrapGuardTest,DeploymentConfigurationTest" test
git diff --check
```

Result:

```text
PASS — MessagingBrokerBootstrapGuardTest, DeploymentConfigurationTest
PASS — git diff --check
```

## Open questions / risks

None.

## Outcome

- Status: COMPLETED
- Plan completion claim: NO
- Residual risk: none beyond accepted ADR-022 / plan text
- Follow-ups (if any): P5 Railway service wiring remains out of band for this
  revision
)
