import { queryOptions } from "@tanstack/react-query";

import { getFulfillment } from "./fulfillmentApi";

export const FULFILLMENT_QUERY_KEY = ["fulfillment"] as const;

/** Non-terminal statuses where a counterparty action may appear without reload. */
export const FULFILLMENT_LIVE_POLL_STATUSES = new Set([
  "IN_PROGRESS",
  "EVIDENCE_REQUIRED",
  "REVIEW_REQUIRED",
]);

export const FULFILLMENT_POLL_INTERVAL_MS = 5_000;

export function fulfillmentDetailQueryKey(
  legalEntityId: string,
  dealId: string,
) {
  return [...FULFILLMENT_QUERY_KEY, legalEntityId, dealId] as const;
}

export function fulfillmentDetailQueryOptions(
  legalEntityId: string | undefined,
  dealId: string | undefined,
  enabled: boolean,
) {
  return queryOptions({
    queryKey: fulfillmentDetailQueryKey(
      legalEntityId ?? "unselected",
      dealId ?? "missing",
    ),
    queryFn: ({ signal }) =>
      getFulfillment(legalEntityId!, dealId!, signal),
    enabled: Boolean(legalEntityId && dealId) && enabled,
    refetchInterval: (query) => {
      const status = query.state.data?.status;
      return status && FULFILLMENT_LIVE_POLL_STATUSES.has(status)
        ? FULFILLMENT_POLL_INTERVAL_MS
        : false;
    },
  });
}
