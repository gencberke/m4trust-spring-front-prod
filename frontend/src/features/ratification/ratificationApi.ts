import type { components } from "../../generated/core-api";
import { postJsonWithFreshCsrf, requestJson } from "../../app/coreApi";

export type RatificationReadiness =
  components["schemas"]["RatificationReadiness"];
export type RatificationPackageStatus =
  components["schemas"]["RatificationPackageStatus"];
export type RatificationCommercialTerms =
  components["schemas"]["RatificationCommercialTerms"];
export type RatificationSnapshotParty =
  components["schemas"]["RatificationSnapshotParty"];
export type RatificationSnapshotRule =
  components["schemas"]["RatificationSnapshotRule"];
export type RatificationSnapshotRuleSet =
  components["schemas"]["RatificationSnapshotRuleSet"];
export type RatificationSnapshotDocument =
  components["schemas"]["RatificationSnapshotDocument"];
export type RatificationPackageSnapshot =
  components["schemas"]["RatificationPackageSnapshot"];
export type RatificationApproval = components["schemas"]["RatificationApproval"];
export type RatificationPackageAvailableActions =
  components["schemas"]["RatificationPackageAvailableActions"];
export type RatificationPackageDetail =
  components["schemas"]["RatificationPackageDetail"];
export type RatificationPackageHistory =
  components["schemas"]["RatificationPackageHistory"];
export type RatificationProjection =
  components["schemas"]["RatificationProjection"];
export type CreateRatificationPackageRequest =
  components["schemas"]["CreateRatificationPackageRequest"];
export type RatificationPackageActionRequest =
  components["schemas"]["RatificationPackageActionRequest"];

const context = (legalEntityId: string) => ({
  "X-M4Trust-Legal-Entity-Id": legalEntityId,
});

export function createRatificationPackage(
  legalEntityId: string,
  dealId: string,
  request: CreateRatificationPackageRequest,
  idempotencyKey: string,
): Promise<RatificationPackageDetail> {
  return postJsonWithFreshCsrf<RatificationPackageDetail>(
    `/deals/${dealId}/ratification-packages`,
    request,
    {
      ...context(legalEntityId),
      "Idempotency-Key": idempotencyKey,
    },
  );
}

export function listRatificationPackages(
  legalEntityId: string,
  dealId: string,
  signal?: AbortSignal,
): Promise<RatificationPackageHistory> {
  return requestJson<RatificationPackageHistory>(
    `/deals/${dealId}/ratification-packages`,
    { signal, headers: context(legalEntityId) },
  );
}

export function getRatificationPackage(
  legalEntityId: string,
  dealId: string,
  ratificationPackageId: string,
  signal?: AbortSignal,
): Promise<RatificationPackageDetail> {
  return requestJson<RatificationPackageDetail>(
    `/deals/${dealId}/ratification-packages/${ratificationPackageId}`,
    { signal, headers: context(legalEntityId) },
  );
}

export function approveRatificationPackage(
  legalEntityId: string,
  dealId: string,
  ratificationPackageId: string,
  request: RatificationPackageActionRequest,
  idempotencyKey: string,
): Promise<RatificationPackageDetail> {
  return postJsonWithFreshCsrf<RatificationPackageDetail>(
    `/deals/${dealId}/ratification-packages/${ratificationPackageId}/approve`,
    request,
    {
      ...context(legalEntityId),
      "Idempotency-Key": idempotencyKey,
    },
  );
}

export function rejectRatificationPackage(
  legalEntityId: string,
  dealId: string,
  ratificationPackageId: string,
  request: RatificationPackageActionRequest,
  idempotencyKey: string,
): Promise<RatificationPackageDetail> {
  return postJsonWithFreshCsrf<RatificationPackageDetail>(
    `/deals/${dealId}/ratification-packages/${ratificationPackageId}/reject`,
    request,
    {
      ...context(legalEntityId),
      "Idempotency-Key": idempotencyKey,
    },
  );
}
