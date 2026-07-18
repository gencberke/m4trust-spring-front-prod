import { queryOptions } from "@tanstack/react-query";

import { listDealDocuments } from "./documentApi";

export const DOCUMENT_QUERY_KEY = ["deal-documents"] as const;

export function dealDocumentHistoryQueryKey(
  legalEntityId: string,
  dealId: string,
) {
  return [...DOCUMENT_QUERY_KEY, legalEntityId, dealId] as const;
}

export function dealDocumentHistoryQueryOptions(
  legalEntityId: string | undefined,
  dealId: string | undefined,
) {
  return queryOptions({
    queryKey: dealDocumentHistoryQueryKey(
      legalEntityId ?? "unselected",
      dealId ?? "missing",
    ),
    queryFn: ({ signal }) =>
      listDealDocuments(legalEntityId!, dealId!, signal),
    enabled: Boolean(legalEntityId && dealId),
  });
}
