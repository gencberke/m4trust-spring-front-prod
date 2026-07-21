import type { components } from "../../generated/core-api";
import { postJsonWithFreshCsrf, requestJson } from "../../app/coreApi";

export type VideoAnalysisDetail = components["schemas"]["VideoAnalysisDetail"];
export type VideoAnalysisStatus = components["schemas"]["VideoAnalysisStatus"];
export type VideoAnalysisResult = components["schemas"]["VideoAnalysisResult"];
export type VideoAnalysisFailureSummary =
  components["schemas"]["VideoAnalysisFailureSummary"];
export type RequestVideoAnalysisRequest =
  components["schemas"]["RequestVideoAnalysisRequest"];

export function getVideoAnalysis(
  legalEntityId: string,
  dealId: string,
  evidenceSubmissionId: string,
  signal?: AbortSignal,
): Promise<VideoAnalysisDetail> {
  return requestJson<VideoAnalysisDetail>(
    `/deals/${dealId}/fulfillment/evidence/${evidenceSubmissionId}/video-analysis`,
    {
      signal,
      headers: { "X-M4Trust-Legal-Entity-Id": legalEntityId },
    },
  );
}

export function requestVideoAnalysis(
  legalEntityId: string,
  dealId: string,
  evidenceSubmissionId: string,
  body: RequestVideoAnalysisRequest,
  idempotencyKey: string,
): Promise<VideoAnalysisDetail> {
  return postJsonWithFreshCsrf<VideoAnalysisDetail>(
    `/deals/${dealId}/fulfillment/evidence/${evidenceSubmissionId}/video-analysis`,
    body,
    {
      "X-M4Trust-Legal-Entity-Id": legalEntityId,
      "Idempotency-Key": idempotencyKey,
    },
  );
}
