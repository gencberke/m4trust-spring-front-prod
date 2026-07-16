import { queryOptions } from "@tanstack/react-query";

import {
  getDeal,
  listDeals,
  type DealListParameters,
} from "./dealApi";

export const DEAL_QUERY_KEY = ["deals"] as const;

export function dealListQueryKey(
  legalEntityId: string,
  parameters: DealListParameters,
) {
  return [
    ...DEAL_QUERY_KEY,
    legalEntityId,
    "list",
    parameters.status ?? "ALL",
    parameters.page,
    parameters.size,
    parameters.sort,
  ] as const;
}

export function dealListQueryOptions(
  legalEntityId: string | undefined,
  parameters: DealListParameters,
) {
  return queryOptions({
    queryKey: dealListQueryKey(legalEntityId ?? "unselected", parameters),
    queryFn: ({ signal }) => listDeals(legalEntityId!, parameters, signal),
    enabled: Boolean(legalEntityId),
  });
}

export function dealDetailQueryKey(
  legalEntityId: string,
  dealId: string,
) {
  return [...DEAL_QUERY_KEY, legalEntityId, "detail", dealId] as const;
}

export function dealDetailQueryOptions(
  legalEntityId: string | undefined,
  dealId: string | undefined,
) {
  return queryOptions({
    queryKey: dealDetailQueryKey(
      legalEntityId ?? "unselected",
      dealId ?? "missing",
    ),
    queryFn: ({ signal }) => getDeal(legalEntityId!, dealId!, signal),
    enabled: Boolean(legalEntityId && dealId),
  });
}
