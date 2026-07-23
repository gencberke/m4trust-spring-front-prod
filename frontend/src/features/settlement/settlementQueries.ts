import { queryOptions } from "@tanstack/react-query";

import { getReleaseOperation, getSettlement } from "./settlementApi";

export const SETTLEMENT_QUERY_KEY = ["settlement"] as const;
export const RELEASE_OPERATION_QUERY_KEY = ["release-operation"] as const;

/** Settlement statuses that may change without a user reload. */
export const SETTLEMENT_LIVE_POLL_STATUSES = new Set([
  "NOT_READY",
  "READY",
  "PROCESSING",
  "ON_HOLD",
]);

/** Release operation statuses polled while outcome is in flight. */
export const RELEASE_OPERATION_LIVE_POLL_STATUSES = new Set([
  "QUEUED",
  "PROCESSING",
  "RECONCILIATION_REQUIRED",
]);

export const SETTLEMENT_POLL_INTERVAL_MS = 3_000;

export function settlementQueryKey(legalEntityId: string, dealId: string) {
  return [...SETTLEMENT_QUERY_KEY, legalEntityId, dealId] as const;
}

export function releaseOperationQueryKey(
  legalEntityId: string,
  operationId: string,
) {
  return [...RELEASE_OPERATION_QUERY_KEY, legalEntityId, operationId] as const;
}

export function settlementQueryOptions(
  legalEntityId: string | undefined,
  dealId: string | undefined,
  enabled: boolean,
) {
  return queryOptions({
    queryKey: settlementQueryKey(
      legalEntityId ?? "unselected",
      dealId ?? "missing",
    ),
    queryFn: ({ signal }) => getSettlement(legalEntityId!, dealId!, signal),
    enabled: Boolean(legalEntityId && dealId) && enabled,
    refetchInterval: (query) => {
      const status = query.state.data?.status;
      return status && SETTLEMENT_LIVE_POLL_STATUSES.has(status)
        ? SETTLEMENT_POLL_INTERVAL_MS
        : false;
    },
  });
}

export function releaseOperationQueryOptions(
  legalEntityId: string | undefined,
  operationId: string | undefined,
) {
  return queryOptions({
    queryKey: releaseOperationQueryKey(
      legalEntityId ?? "unselected",
      operationId ?? "missing",
    ),
    queryFn: ({ signal }) =>
      getReleaseOperation(legalEntityId!, operationId!, signal),
    enabled: Boolean(legalEntityId && operationId),
  });
}
