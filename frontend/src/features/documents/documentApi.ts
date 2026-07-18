import type { components } from "../../generated/core-api";
import {
  postJsonWithFreshCsrf,
  postJsonWithoutBodyWithFreshCsrf,
  requestJson,
} from "../../app/coreApi";

export type DocumentMediaType = components["schemas"]["DocumentMediaType"];
export type DocumentStatus = components["schemas"]["DocumentStatus"];
export type CreateDocumentUploadIntentRequest =
  components["schemas"]["CreateDocumentUploadIntentRequest"];
export type FinalizeDocumentUploadRequest =
  components["schemas"]["FinalizeDocumentUploadRequest"];
export type DocumentAvailableActions =
  components["schemas"]["DocumentAvailableActions"];
export type PendingDealDocument = components["schemas"]["PendingDealDocument"];
export type AvailableDealDocument =
  components["schemas"]["AvailableDealDocument"];
export type HistoricalDealDocument =
  components["schemas"]["HistoricalDealDocument"];
export type DealDocumentHistory = components["schemas"]["DealDocumentHistory"];
export type DealDocumentHistoryItem = DealDocumentHistory["items"][number];
export type DocumentUploadIntent = components["schemas"]["DocumentUploadIntent"];
export type DocumentDownloadLink = components["schemas"]["DocumentDownloadLink"];

export function createDealDocumentUploadIntent(
  legalEntityId: string,
  dealId: string,
  request: CreateDocumentUploadIntentRequest,
): Promise<DocumentUploadIntent> {
  return postJsonWithFreshCsrf<DocumentUploadIntent>(
    `/deals/${dealId}/documents/upload-intents`,
    request,
    { "X-M4Trust-Legal-Entity-Id": legalEntityId },
  );
}

export function listDealDocuments(
  legalEntityId: string,
  dealId: string,
  signal?: AbortSignal,
): Promise<DealDocumentHistory> {
  return requestJson<DealDocumentHistory>(`/deals/${dealId}/documents`, {
    signal,
    headers: { "X-M4Trust-Legal-Entity-Id": legalEntityId },
  });
}

export function finalizeDocumentUpload(
  legalEntityId: string,
  documentId: string,
  request: FinalizeDocumentUploadRequest,
  idempotencyKey: string,
): Promise<AvailableDealDocument> {
  return postJsonWithFreshCsrf<AvailableDealDocument>(
    `/documents/${documentId}/finalize`,
    request,
    {
      "X-M4Trust-Legal-Entity-Id": legalEntityId,
      "Idempotency-Key": idempotencyKey,
    },
  );
}

export function createDocumentDownloadLink(
  legalEntityId: string,
  documentId: string,
): Promise<DocumentDownloadLink> {
  return postJsonWithoutBodyWithFreshCsrf<DocumentDownloadLink>(
    `/documents/${documentId}/download-link`,
    { "X-M4Trust-Legal-Entity-Id": legalEntityId },
  );
}
