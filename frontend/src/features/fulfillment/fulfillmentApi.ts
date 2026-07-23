import type { components } from "../../generated/core-api";
import {
  postJsonWithFreshCsrf,
  postJsonWithoutBodyWithFreshCsrf,
  requestJson,
} from "../../app/coreApi";

export type FulfillmentStatus = components["schemas"]["FulfillmentStatus"];
export type EvidenceSubmissionStatus =
  components["schemas"]["EvidenceSubmissionStatus"];
export type EvidenceType = components["schemas"]["EvidenceType"];
export type EvidenceMediaType = components["schemas"]["EvidenceMediaType"];
export type FulfillmentDetail = components["schemas"]["FulfillmentDetail"];
export type EvidenceSubmission = components["schemas"]["EvidenceSubmission"];
export type EvidenceUploadIntent =
  components["schemas"]["EvidenceUploadIntent"];
export type EvidenceDownloadLink =
  components["schemas"]["EvidenceDownloadLink"];
export type StartFulfillmentRequest =
  components["schemas"]["StartFulfillmentRequest"];
export type CreateEvidenceUploadIntentRequest =
  components["schemas"]["CreateEvidenceUploadIntentRequest"];
export type FinalizeEvidenceUploadRequest =
  components["schemas"]["FinalizeEvidenceUploadRequest"];
export type AcceptEvidenceRequest =
  components["schemas"]["AcceptEvidenceRequest"];
export type AcceptWithoutEvidenceRequest =
  components["schemas"]["AcceptWithoutEvidenceRequest"];
export type RejectEvidenceRequest =
  components["schemas"]["RejectEvidenceRequest"];
export type EvidencePolicy = components["schemas"]["EvidencePolicy"];

export function startFulfillment(
  legalEntityId: string,
  dealId: string,
  request: StartFulfillmentRequest,
  idempotencyKey: string,
): Promise<FulfillmentDetail> {
  return postJsonWithFreshCsrf<FulfillmentDetail>(
    `/deals/${dealId}/fulfillment`,
    request,
    {
      "X-M4Trust-Legal-Entity-Id": legalEntityId,
      "Idempotency-Key": idempotencyKey,
    },
  );
}

export function getFulfillment(
  legalEntityId: string,
  dealId: string,
  signal?: AbortSignal,
): Promise<FulfillmentDetail> {
  return requestJson<FulfillmentDetail>(`/deals/${dealId}/fulfillment`, {
    signal,
    headers: { "X-M4Trust-Legal-Entity-Id": legalEntityId },
  });
}

export function createEvidenceUploadIntent(
  legalEntityId: string,
  dealId: string,
  request: CreateEvidenceUploadIntentRequest,
): Promise<EvidenceUploadIntent> {
  return postJsonWithFreshCsrf<EvidenceUploadIntent>(
    `/deals/${dealId}/fulfillment/evidence/upload-intents`,
    request,
    { "X-M4Trust-Legal-Entity-Id": legalEntityId },
  );
}

export function finalizeEvidenceUpload(
  legalEntityId: string,
  dealId: string,
  evidenceSubmissionId: string,
  request: FinalizeEvidenceUploadRequest,
  idempotencyKey: string,
): Promise<EvidenceSubmission> {
  return postJsonWithFreshCsrf<EvidenceSubmission>(
    `/deals/${dealId}/fulfillment/evidence/${evidenceSubmissionId}/finalize`,
    request,
    {
      "X-M4Trust-Legal-Entity-Id": legalEntityId,
      "Idempotency-Key": idempotencyKey,
    },
  );
}

export function createEvidenceDownloadLink(
  legalEntityId: string,
  dealId: string,
  evidenceSubmissionId: string,
): Promise<EvidenceDownloadLink> {
  return postJsonWithoutBodyWithFreshCsrf<EvidenceDownloadLink>(
    `/deals/${dealId}/fulfillment/evidence/${evidenceSubmissionId}/download-link`,
    { "X-M4Trust-Legal-Entity-Id": legalEntityId },
  );
}

export function acceptEvidence(
  legalEntityId: string,
  dealId: string,
  evidenceSubmissionId: string,
  request: AcceptEvidenceRequest,
  idempotencyKey: string,
): Promise<EvidenceSubmission> {
  return postJsonWithFreshCsrf<EvidenceSubmission>(
    `/deals/${dealId}/fulfillment/evidence/${evidenceSubmissionId}/accept`,
    request,
    {
      "X-M4Trust-Legal-Entity-Id": legalEntityId,
      "Idempotency-Key": idempotencyKey,
    },
  );
}

export function rejectEvidence(
  legalEntityId: string,
  dealId: string,
  evidenceSubmissionId: string,
  request: RejectEvidenceRequest,
  idempotencyKey: string,
): Promise<EvidenceSubmission> {
  return postJsonWithFreshCsrf<EvidenceSubmission>(
    `/deals/${dealId}/fulfillment/evidence/${evidenceSubmissionId}/reject`,
    request,
    {
      "X-M4Trust-Legal-Entity-Id": legalEntityId,
      "Idempotency-Key": idempotencyKey,
    },
  );
}

export function acceptFulfillmentWithoutEvidence(
  legalEntityId: string,
  dealId: string,
  request: AcceptWithoutEvidenceRequest,
  idempotencyKey: string,
): Promise<FulfillmentDetail> {
  return postJsonWithFreshCsrf<FulfillmentDetail>(
    `/deals/${dealId}/fulfillment/accept-without-evidence`,
    request,
    {
      "X-M4Trust-Legal-Entity-Id": legalEntityId,
      "Idempotency-Key": idempotencyKey,
    },
  );
}
