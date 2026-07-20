import { queryOptions } from "@tanstack/react-query";

import { getVideoAnalysis } from "./videoAnalysisApi";

const QUEUED_STATUS = "QUEUED";
const VIDEO_ANALYSIS_POLL_INTERVAL_MS = 2_000;

export function videoAnalysisQueryKey(
  legalEntityId: string,
  dealId: string,
  evidenceSubmissionId: string,
) {
  return [
    "deals",
    legalEntityId,
    "detail",
    dealId,
    "evidence",
    evidenceSubmissionId,
    "video-analysis",
  ] as const;
}

export function videoAnalysisQueryOptions(
  legalEntityId: string | undefined,
  dealId: string | undefined,
  evidenceSubmissionId: string | undefined,
) {
  return queryOptions({
    queryKey: videoAnalysisQueryKey(
      legalEntityId ?? "unselected",
      dealId ?? "missing",
      evidenceSubmissionId ?? "missing",
    ),
    queryFn: ({ signal }) =>
      getVideoAnalysis(
        legalEntityId!,
        dealId!,
        evidenceSubmissionId!,
        signal,
      ),
    enabled: Boolean(legalEntityId && dealId && evidenceSubmissionId),
    refetchInterval: (query) =>
      query.state.data?.status === QUEUED_STATUS
        ? VIDEO_ANALYSIS_POLL_INTERVAL_MS
        : false,
  });
}
