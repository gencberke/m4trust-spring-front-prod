import type { components } from "../../generated/core-api";
import { postJsonWithFreshCsrf, requestJson } from "../../app/coreApi";

export type FundingStatus = components["schemas"]["FundingStatus"];
export type FundingUnitStatus = components["schemas"]["FundingUnitStatus"];
export type PaymentOperationStatus =
  components["schemas"]["PaymentOperationStatus"];
export type FundingUnitAvailableActions =
  components["schemas"]["FundingUnitAvailableActions"];
export type PaymentOperationAvailableActions =
  components["schemas"]["PaymentOperationAvailableActions"];
export type PaymentOperation = components["schemas"]["PaymentOperation"];
export type FundingUnit = components["schemas"]["FundingUnit"];
export type FundingPlanDetail = components["schemas"]["FundingPlanDetail"];
export type DealFundingSummary = components["schemas"]["DealFundingSummary"];
export type CreateFundingPlanRequest =
  components["schemas"]["CreateFundingPlanRequest"];
export type InitiatePaymentOperationRequest =
  components["schemas"]["InitiatePaymentOperationRequest"];
export type ReconcilePaymentOperationRequest =
  components["schemas"]["ReconcilePaymentOperationRequest"];

const context = (legalEntityId: string) => ({
  "X-M4Trust-Legal-Entity-Id": legalEntityId,
});

export function createFundingPlan(
  legalEntityId: string,
  dealId: string,
  request: CreateFundingPlanRequest,
  idempotencyKey: string,
): Promise<FundingPlanDetail> {
  return postJsonWithFreshCsrf<FundingPlanDetail>(
    `/deals/${dealId}/funding-plan`,
    request,
    {
      ...context(legalEntityId),
      "Idempotency-Key": idempotencyKey,
    },
  );
}

export function getFundingPlan(
  legalEntityId: string,
  dealId: string,
  signal?: AbortSignal,
): Promise<FundingPlanDetail> {
  return requestJson<FundingPlanDetail>(`/deals/${dealId}/funding-plan`, {
    signal,
    headers: context(legalEntityId),
  });
}

export function initiatePaymentOperation(
  legalEntityId: string,
  fundingUnitId: string,
  request: InitiatePaymentOperationRequest,
  idempotencyKey: string,
): Promise<PaymentOperation> {
  return postJsonWithFreshCsrf<PaymentOperation>(
    `/funding-units/${fundingUnitId}/payment-operations`,
    request,
    {
      ...context(legalEntityId),
      "Idempotency-Key": idempotencyKey,
    },
  );
}

export function getPaymentOperation(
  legalEntityId: string,
  paymentOperationId: string,
  signal?: AbortSignal,
): Promise<PaymentOperation> {
  return requestJson<PaymentOperation>(
    `/payment-operations/${paymentOperationId}`,
    { signal, headers: context(legalEntityId) },
  );
}

export function reconcilePaymentOperation(
  legalEntityId: string,
  paymentOperationId: string,
  request: ReconcilePaymentOperationRequest,
  idempotencyKey: string,
): Promise<PaymentOperation> {
  return postJsonWithFreshCsrf<PaymentOperation>(
    `/payment-operations/${paymentOperationId}/reconcile`,
    request,
    {
      ...context(legalEntityId),
      "Idempotency-Key": idempotencyKey,
    },
  );
}
