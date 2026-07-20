import type { components } from "../../generated/core-api";
import { postJsonWithFreshCsrf, requestJson } from "../../app/coreApi";

export type DealExtractionReview =
  components["schemas"]["DealExtractionReview"];
export type AcceptExtractionReviewRequest =
  components["schemas"]["AcceptExtractionReviewRequest"];
export type RuleSetVersion = components["schemas"]["RuleSetVersion"];
export type RuleSetVersionHistory =
  components["schemas"]["RuleSetVersionHistory"];

const context = (legalEntityId: string) => ({
  "X-M4Trust-Legal-Entity-Id": legalEntityId,
});

export function getExtractionReview(
  legalEntityId: string,
  dealId: string,
  signal?: AbortSignal,
) {
  return requestJson<DealExtractionReview>(
    `/deals/${dealId}/extraction-review`,
    { signal, headers: context(legalEntityId) },
  );
}

export function listRuleSets(
  legalEntityId: string,
  dealId: string,
  signal?: AbortSignal,
) {
  return requestJson<RuleSetVersionHistory>(
    `/deals/${dealId}/rule-set-versions`,
    { signal, headers: context(legalEntityId) },
  );
}

export function getRuleSetVersion(
  legalEntityId: string,
  dealId: string,
  ruleSetVersionId: string,
  signal?: AbortSignal,
) {
  return requestJson<RuleSetVersion>(
    `/deals/${dealId}/rule-set-versions/${ruleSetVersionId}`,
    { signal, headers: context(legalEntityId) },
  );
}

export function acceptExtractionReview(
  legalEntityId: string,
  dealId: string,
  request: AcceptExtractionReviewRequest,
  idempotencyKey: string,
) {
  return postJsonWithFreshCsrf<RuleSetVersion>(
    `/deals/${dealId}/extraction-review/accept`,
    request,
    {
      ...context(legalEntityId),
      "Idempotency-Key": idempotencyKey,
    },
  );
}
