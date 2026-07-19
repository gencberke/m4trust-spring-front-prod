import { queryOptions } from "@tanstack/react-query";

import { getDealDocumentAnalysis } from "./analysisApi";

const ACTIVE_ANALYSIS_STATUSES = new Set(["QUEUED", "PROCESSING"]);
const ANALYSIS_POLL_INTERVAL_MS = 2_000;

export function dealDocumentAnalysisQueryKey(
  legalEntityId: string,
  dealId: string,
) {
  return ["deals", legalEntityId, "detail", dealId, "document-analysis"] as const;
}

export function dealDocumentAnalysisQueryOptions(
  legalEntityId: string | undefined,
  dealId: string | undefined,
) {
  return queryOptions({
    queryKey: dealDocumentAnalysisQueryKey(
      legalEntityId ?? "unselected",
      dealId ?? "missing",
    ),
    queryFn: ({ signal }) => getDealDocumentAnalysis(legalEntityId!, dealId!, signal),
    enabled: Boolean(legalEntityId && dealId),
    refetchInterval: (query) =>
      query.state.data && ACTIVE_ANALYSIS_STATUSES.has(query.state.data.status)
        ? ANALYSIS_POLL_INTERVAL_MS
        : false,
  });
}
