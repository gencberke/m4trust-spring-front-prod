import { queryOptions } from "@tanstack/react-query";

import { listRatificationPackages } from "./ratificationApi";

export const RATIFICATION_QUERY_KEY = ["ratification-packages"] as const;

export function ratificationPackageHistoryQueryKey(
  legalEntityId: string,
  dealId: string,
) {
  return [...RATIFICATION_QUERY_KEY, legalEntityId, dealId] as const;
}

export function ratificationPackageHistoryQueryOptions(
  legalEntityId: string | undefined,
  dealId: string | undefined,
) {
  return queryOptions({
    queryKey: ratificationPackageHistoryQueryKey(
      legalEntityId ?? "unselected",
      dealId ?? "missing",
    ),
    queryFn: ({ signal }) =>
      listRatificationPackages(legalEntityId!, dealId!, signal),
    enabled: Boolean(legalEntityId && dealId),
  });
}
