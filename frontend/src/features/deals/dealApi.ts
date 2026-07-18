import type { components } from "../../generated/core-api";
import {
  patchJsonWithFreshCsrf,
  postJsonWithFreshCsrf,
  postJsonWithoutBodyWithFreshCsrf,
  requestJson,
} from "../../app/coreApi";

export type CreateDealRequest = components["schemas"]["CreateDealRequest"];
export type UpdateDealRequest = components["schemas"]["UpdateDealRequest"];
export type UpdateDealPartiesRequest = components["schemas"]["UpdateDealPartiesRequest"];
export type DealStatus = components["schemas"]["DealStatus"];
export type DealSort = components["parameters"]["DealSort"];
export type DealSummary = components["schemas"]["DealSummary"];
export type DealDetail = components["schemas"]["DealDetail"];
export type DealPage = components["schemas"]["DealPage"];

export interface DealListParameters {
  status?: DealStatus;
  page: number;
  size: number;
  sort: DealSort;
}

export function listDeals(
  legalEntityId: string,
  parameters: DealListParameters,
  signal?: AbortSignal,
): Promise<DealPage> {
  const search = new URLSearchParams({
    page: String(parameters.page),
    size: String(parameters.size),
    sort: parameters.sort,
  });
  if (parameters.status) {
    search.set("status", parameters.status);
  }
  return requestJson<DealPage>(`/deals?${search.toString()}`, {
    signal,
    headers: { "X-M4Trust-Legal-Entity-Id": legalEntityId },
  });
}

export function createDeal(
  legalEntityId: string,
  request: CreateDealRequest,
): Promise<DealDetail> {
  return postJsonWithFreshCsrf<DealDetail>("/deals", request, {
    "X-M4Trust-Legal-Entity-Id": legalEntityId,
  });
}

export function getDeal(
  legalEntityId: string,
  dealId: string,
  signal?: AbortSignal,
): Promise<DealDetail> {
  return requestJson<DealDetail>(`/deals/${dealId}`, {
    signal,
    headers: { "X-M4Trust-Legal-Entity-Id": legalEntityId },
  });
}

export function updateDeal(
  legalEntityId: string,
  dealId: string,
  request: UpdateDealRequest,
): Promise<DealDetail> {
  return patchJsonWithFreshCsrf<DealDetail>(`/deals/${dealId}`, request, {
    "X-M4Trust-Legal-Entity-Id": legalEntityId,
  });
}

export function updateDealParties(
  legalEntityId: string,
  dealId: string,
  request: UpdateDealPartiesRequest,
): Promise<DealDetail> {
  return patchJsonWithFreshCsrf<DealDetail>(`/deals/${dealId}/parties`, request, {
    "X-M4Trust-Legal-Entity-Id": legalEntityId,
  });
}

export function cancelDeal(
  legalEntityId: string,
  dealId: string,
): Promise<DealDetail> {
  return postJsonWithoutBodyWithFreshCsrf<DealDetail>(
    `/deals/${dealId}/cancel`,
    { "X-M4Trust-Legal-Entity-Id": legalEntityId },
  );
}
