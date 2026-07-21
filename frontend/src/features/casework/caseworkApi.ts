import type { components } from "../../generated/core-api";
import { postJsonWithFreshCsrf, requestJson } from "../../app/coreApi";

export type DisputeReasonCode = components["schemas"]["DisputeReasonCode"];
export type DisputeStatus = components["schemas"]["DisputeStatus"];
export type DisputeSummary = components["schemas"]["DisputeSummary"];
export type DisputeDetail = components["schemas"]["DisputeDetail"];
export type DisputePage = components["schemas"]["DisputePage"];
export type DisputeComment = components["schemas"]["DisputeComment"];
export type DisputeCommentPage = components["schemas"]["DisputeCommentPage"];
export type OpenDisputeRequest = components["schemas"]["OpenDisputeRequest"];
export type CreateDisputeCommentRequest =
  components["schemas"]["CreateDisputeCommentRequest"];
export type AcknowledgeDisputeRequest =
  components["schemas"]["AcknowledgeDisputeRequest"];
export type WithdrawDisputeRequest = components["schemas"]["WithdrawDisputeRequest"];
export type DisputeCommentSort = components["parameters"]["DisputeCommentSort"];
export type DisputeSort = components["parameters"]["DisputeSort"];

const context = (legalEntityId: string) => ({
  "X-M4Trust-Legal-Entity-Id": legalEntityId,
});

export function listDisputes(
  legalEntityId: string,
  dealId: string,
  page: number,
  sort: DisputeSort = "openedAt,desc",
  signal?: AbortSignal,
): Promise<DisputePage> {
  const search = new URLSearchParams({
    page: String(page),
    size: "20",
    sort,
  });
  return requestJson<DisputePage>(`/deals/${dealId}/disputes?${search.toString()}`, {
    signal,
    headers: context(legalEntityId),
  });
}

export function openDispute(
  legalEntityId: string,
  dealId: string,
  request: OpenDisputeRequest,
  idempotencyKey: string,
): Promise<DisputeDetail> {
  return postJsonWithFreshCsrf<DisputeDetail>(`/deals/${dealId}/disputes`, request, {
    ...context(legalEntityId),
    "Idempotency-Key": idempotencyKey,
  });
}

export function getDispute(
  legalEntityId: string,
  dealId: string,
  disputeId: string,
  signal?: AbortSignal,
): Promise<DisputeDetail> {
  return requestJson<DisputeDetail>(`/deals/${dealId}/disputes/${disputeId}`, {
    signal,
    headers: context(legalEntityId),
  });
}

export function listDisputeComments(
  legalEntityId: string,
  dealId: string,
  disputeId: string,
  page: number,
  sort: DisputeCommentSort = "createdAt,asc",
  signal?: AbortSignal,
): Promise<DisputeCommentPage> {
  const search = new URLSearchParams({
    page: String(page),
    size: "20",
    sort,
  });
  return requestJson<DisputeCommentPage>(
    `/deals/${dealId}/disputes/${disputeId}/comments?${search.toString()}`,
    { signal, headers: context(legalEntityId) },
  );
}

export function createDisputeComment(
  legalEntityId: string,
  dealId: string,
  disputeId: string,
  request: CreateDisputeCommentRequest,
  idempotencyKey: string,
): Promise<DisputeComment> {
  return postJsonWithFreshCsrf<DisputeComment>(
    `/deals/${dealId}/disputes/${disputeId}/comments`,
    request,
    {
      ...context(legalEntityId),
      "Idempotency-Key": idempotencyKey,
    },
  );
}

export function acknowledgeDispute(
  legalEntityId: string,
  dealId: string,
  disputeId: string,
  request: AcknowledgeDisputeRequest,
  idempotencyKey: string,
): Promise<DisputeDetail> {
  return postJsonWithFreshCsrf<DisputeDetail>(
    `/deals/${dealId}/disputes/${disputeId}/acknowledge`,
    request,
    {
      ...context(legalEntityId),
      "Idempotency-Key": idempotencyKey,
    },
  );
}

export function withdrawDispute(
  legalEntityId: string,
  dealId: string,
  disputeId: string,
  request: WithdrawDisputeRequest,
  idempotencyKey: string,
): Promise<DisputeDetail> {
  return postJsonWithFreshCsrf<DisputeDetail>(
    `/deals/${dealId}/disputes/${disputeId}/withdraw`,
    request,
    {
      ...context(legalEntityId),
      "Idempotency-Key": idempotencyKey,
    },
  );
}
