import { queryOptions } from "@tanstack/react-query";

import { getFulfillment } from "./fulfillmentApi";

export const FULFILLMENT_QUERY_KEY = ["fulfillment"] as const;

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
  });
}
