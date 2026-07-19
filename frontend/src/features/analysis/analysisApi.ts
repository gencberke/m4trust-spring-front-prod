import type { components } from "../../generated/core-api";
import {
  postJsonWithoutBodyWithFreshCsrf,
  requestJson,
} from "../../app/coreApi";

export type DealDocumentAnalysis = components["schemas"]["DealDocumentAnalysis"];
export type DocumentAnalysisStatus = components["schemas"]["DocumentAnalysisStatus"];
export type DocumentAnalysisResult = components["schemas"]["DocumentAnalysisResult"];
export type ExtractedRuleValue = components["schemas"]["ExtractedRuleValue"];
export type ExtractionSourceReference = components["schemas"]["ExtractionSourceReference"];

export function getDealDocumentAnalysis(
  legalEntityId: string,
  dealId: string,
  signal?: AbortSignal,
): Promise<DealDocumentAnalysis> {
  return requestJson<DealDocumentAnalysis>(`/deals/${dealId}/document-analysis`, {
    signal,
    headers: { "X-M4Trust-Legal-Entity-Id": legalEntityId },
  });
}

export function requestDealDocumentAnalysis(
  legalEntityId: string,
  dealId: string,
  idempotencyKey: string,
): Promise<DealDocumentAnalysis> {
  return postJsonWithoutBodyWithFreshCsrf<DealDocumentAnalysis>(
    `/deals/${dealId}/document-analysis`,
    {
      "X-M4Trust-Legal-Entity-Id": legalEntityId,
      "Idempotency-Key": idempotencyKey,
    },
  );
}
