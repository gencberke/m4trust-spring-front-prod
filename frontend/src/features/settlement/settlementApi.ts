import type { components } from "../../generated/core-api";
import { postJsonWithFreshCsrf, requestJson } from "../../app/coreApi";

export type SettlementStatus = components["schemas"]["SettlementStatus"];
export type ReleaseOperationStatus =
  components["schemas"]["ReleaseOperationStatus"];
export type SettlementDetail = components["schemas"]["SettlementDetail"];
export type ReleaseOperation = components["schemas"]["ReleaseOperation"];
export type RequestSettlementReleaseRequest =
  components["schemas"]["RequestSettlementReleaseRequest"];
export type ReconcileReleaseOperationRequest =
  components["schemas"]["ReconcileReleaseOperationRequest"];

const context = (legalEntityId: string) => ({
  "X-M4Trust-Legal-Entity-Id": legalEntityId,
});

export function getSettlement(
  legalEntityId: string,
  dealId: string,
  signal?: AbortSignal,
): Promise<SettlementDetail> {
  return requestJson<SettlementDetail>(`/deals/${dealId}/settlement`, {
    signal,
    headers: context(legalEntityId),
  });
}

export function requestSettlementRelease(
  legalEntityId: string,
  dealId: string,
  request: RequestSettlementReleaseRequest,
  idempotencyKey: string,
): Promise<ReleaseOperation> {
  return postJsonWithFreshCsrf<ReleaseOperation>(
    `/deals/${dealId}/settlement/release`,
    request,
    {
      ...context(legalEntityId),
      "Idempotency-Key": idempotencyKey,
    },
  );
}

export function getReleaseOperation(
  legalEntityId: string,
  operationId: string,
  signal?: AbortSignal,
): Promise<ReleaseOperation> {
  return requestJson<ReleaseOperation>(
    `/release-operations/${operationId}`,
    { signal, headers: context(legalEntityId) },
  );
}

export function reconcileReleaseOperation(
  legalEntityId: string,
  operationId: string,
  request: ReconcileReleaseOperationRequest,
  idempotencyKey: string,
): Promise<ReleaseOperation> {
  return postJsonWithFreshCsrf<ReleaseOperation>(
    `/release-operations/${operationId}/reconcile`,
    request,
    {
      ...context(legalEntityId),
      "Idempotency-Key": idempotencyKey,
    },
  );
}
